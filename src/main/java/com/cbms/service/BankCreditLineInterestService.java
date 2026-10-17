package com.cbms.service;

import com.cbms.dto.*;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class BankCreditLineInterestService {

    private static final BigDecimal HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal DAY_BASIS =
            new BigDecimal("365");

    private final JdbcTemplate jdbc;

    public BankCreditLineInterestService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public BankCreditLineInterestAccrualResponse accrue(
            Long facId,
            BankCreditLineInterestAccrualRequest request,
            Authentication authentication
    ) {
        List<Map<String, Object>> facilities = jdbc.queryForList(
                """
                SELECT *
                FROM public.bank_credit_line_facility
                WHERE cl_fac_id = ?
                FOR UPDATE
                """,
                facId
        );

        if (facilities.isEmpty()) {
            throw notFound("Credit line facility not found");
        }

        Map<String, Object> facility = facilities.get(0);

        if (!"ACTIVE".equals(facility.get("cl_fac_sts"))) {
            throw conflict("Facility must be ACTIVE");
        }

        String facilityType =
                (String) facility.get("cl_fac_typ");

        if (!"REVOLVING_CREDIT".equals(facilityType)
                && !"FLOOR_STOCKING".equals(facilityType)) {
            throw conflict("Unsupported facility type");
        }

        if (!"MYR".equals(facility.get("ccy_cd"))) {
            throw conflict("Only MYR facilities are supported");
        }

        if (!"FIXED".equals(facility.get("rate_typ"))) {
            throw conflict(
                    "Floating-rate accrual is not yet supported"
            );
        }

        LocalDate accrualDate = request.accrualDate();

        if (accrualDate.isAfter(LocalDate.now())) {
            throw conflict("Future-dated accrual is not allowed");
        }

        LocalDate effectiveDate =
                ((Date) facility.get("eff_dt")).toLocalDate();

        LocalDate expiryDate =
                ((Date) facility.get("expiry_dt")).toLocalDate();

        if (accrualDate.isBefore(effectiveDate)
                || !accrualDate.isBefore(expiryDate)) {
            throw conflict(
                    "Accrual date is outside the facility effective period"
            );
        }

        // Current-state snapshot only:
        // Historical backdating is deliberately not supported.
        if (!accrualDate.equals(LocalDate.now())) {
            throw conflict(
                    "Only current-date accrual is supported"
            );
        }

        List<Long> existing = jdbc.queryForList(
                """
                SELECT accrual_id
                FROM public.bank_credit_line_interest_accrual
                WHERE cl_fac_id = ?
                  AND accrual_dt = ?
                """,
                Long.class,
                facId,
                accrualDate
        );

        if (!existing.isEmpty()) {
            throw conflict(
                    "Interest already accrued for this facility and date"
            );
        }

        BigDecimal principal =
                (BigDecimal) facility.get("utilized_amt");

        BigDecimal annualRate =
                (BigDecimal) facility.get("eff_rate");

        if (principal == null || principal.signum() < 0) {
            throw conflict("Invalid outstanding principal");
        }

        if (annualRate == null || annualRate.signum() < 0) {
            throw conflict("Invalid annual interest rate");
        }

        BigDecimal dailyInterest = principal
                .multiply(annualRate)
                .divide(
                        HUNDRED.multiply(DAY_BASIS),
                        10,
                        RoundingMode.HALF_UP
                );

        List<Long> insertedIds = jdbc.queryForList(
                """
                INSERT INTO public.bank_credit_line_interest_accrual
                (
                    cl_fac_id,
                    accrual_dt,
                    principal_amt,
                    annual_rate_pct,
                    day_basis,
                    daily_interest_amt,
                    accrual_sts,
                    crt_by
                )
                VALUES (?, ?, ?, ?, 365, ?, 'ACCRUED', ?)
                ON CONFLICT (cl_fac_id, accrual_dt)
                DO NOTHING
                RETURNING accrual_id
                """,
                Long.class,
                facId,
                accrualDate,
                principal,
                annualRate,
                dailyInterest,
                authentication.getName()
        );

        if (insertedIds.isEmpty()) {
            throw conflict(
                    "Interest already accrued for this facility and date"
            );
        }

        Long accrualId = insertedIds.get(0);

        return getAccrualInternal(accrualId);
    }

    @Transactional(readOnly = true)
    public List<BankCreditLineInterestAccrualResponse> list(
            Long facId
    ) {
        ensureFacilityExists(facId);

        return jdbc.query(
                """
                SELECT *
                FROM public.bank_credit_line_interest_accrual
                WHERE cl_fac_id = ?
                ORDER BY accrual_dt DESC, accrual_id DESC
                """,
                (rs, rowNum) -> mapAccrual(rs),
                facId
        );
    }

    @Transactional(readOnly = true)
    public BankCreditLineInterestSummaryResponse summary(
            Long facId
    ) {
        List<Map<String, Object>> facilities = jdbc.queryForList(
                """
                SELECT
                    cl_fac_id,
                    cl_fac_ref,
                    cl_fac_typ,
                    cl_fac_sts,
                    utilized_amt,
                    eff_rate
                FROM public.bank_credit_line_facility
                WHERE cl_fac_id = ?
                """,
                facId
        );

        if (facilities.isEmpty()) {
            throw notFound("Credit line facility not found");
        }

        Map<String, Object> facility = facilities.get(0);

        Map<String, Object> totals = jdbc.queryForMap(
                """
                SELECT
                    COUNT(*) AS accrued_days,
                    COALESCE(
                        SUM(daily_interest_amt),
                        0
                    ) AS total_interest
                FROM public.bank_credit_line_interest_accrual
                WHERE cl_fac_id = ?
                """,
                facId
        );

        BigDecimal total =
                (BigDecimal) totals.get("total_interest");

        return new BankCreditLineInterestSummaryResponse(
                ((Number) facility.get("cl_fac_id")).longValue(),
                (String) facility.get("cl_fac_ref"),
                (String) facility.get("cl_fac_typ"),
                (String) facility.get("cl_fac_sts"),
                (BigDecimal) facility.get("utilized_amt"),
                (BigDecimal) facility.get("eff_rate"),
                ((Number) totals.get("accrued_days")).longValue(),
                total,
                total.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private BankCreditLineInterestAccrualResponse getAccrualInternal(
            Long accrualId
    ) {
        List<BankCreditLineInterestAccrualResponse> results =
                jdbc.query(
                        """
                        SELECT *
                        FROM public.bank_credit_line_interest_accrual
                        WHERE accrual_id = ?
                        """,
                        (rs, rowNum) -> mapAccrual(rs),
                        accrualId
                );

        if (results.isEmpty()) {
            throw notFound("Interest accrual not found");
        }

        return results.get(0);
    }

    private BankCreditLineInterestAccrualResponse mapAccrual(
            java.sql.ResultSet rs
    ) throws java.sql.SQLException {
        return new BankCreditLineInterestAccrualResponse(
                rs.getLong("accrual_id"),
                rs.getLong("cl_fac_id"),
                rs.getDate("accrual_dt").toLocalDate(),
                rs.getBigDecimal("principal_amt"),
                rs.getBigDecimal("annual_rate_pct"),
                rs.getInt("day_basis"),
                rs.getBigDecimal("daily_interest_amt"),
                rs.getString("accrual_sts"),
                rs.getObject("crt_dt", OffsetDateTime.class),
                rs.getString("crt_by")
        );
    }

    private void ensureFacilityExists(Long facId) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.bank_credit_line_facility
                WHERE cl_fac_id = ?
                """,
                Integer.class,
                facId
        );

        if (count == null || count == 0) {
            throw notFound("Credit line facility not found");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }
}
