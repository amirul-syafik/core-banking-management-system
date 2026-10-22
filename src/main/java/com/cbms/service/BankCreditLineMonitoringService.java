package com.cbms.service;

import com.cbms.dto.BankCreditLineMonitoringResponse;
import com.cbms.dto.BankCreditLineStatusActionRequest;
import com.cbms.dto.BankCreditLineStatusHistoryResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class BankCreditLineMonitoringService {

    private final JdbcTemplate jdbc;
    private final BankCreditLineEligibilityGuard eligibilityGuard;

    public BankCreditLineMonitoringService(
            JdbcTemplate jdbc,
            BankCreditLineEligibilityGuard eligibilityGuard
    ) {
        this.jdbc = jdbc;
        this.eligibilityGuard = eligibilityGuard;
    }

    @Transactional(readOnly = true)
    public List<BankCreditLineMonitoringResponse> list() {
        return jdbc.query(
                monitoringSql() + " ORDER BY f.cl_fac_id DESC",
                (rs, rowNum) -> mapMonitoring(rs)
        );
    }

    @Transactional(readOnly = true)
    public BankCreditLineMonitoringResponse get(Long facId) {
        List<BankCreditLineMonitoringResponse> rows = jdbc.query(
                monitoringSql() + " WHERE f.cl_fac_id = ?",
                (rs, rowNum) -> mapMonitoring(rs),
                facId
        );

        if (rows.isEmpty()) {
            throw notFound("Credit line facility not found");
        }

        return rows.get(0);
    }

    @Transactional
    public BankCreditLineMonitoringResponse suspend(
            Long facId,
            BankCreditLineStatusActionRequest request,
            Authentication authentication
    ) {
        Map<String, Object> facility = lockFacility(facId);

        String currentStatus = (String) facility.get("cl_fac_sts");

        if (!"ACTIVE".equals(currentStatus)) {
            throw conflict("Only ACTIVE facilities can be suspended");
        }

        changeStatus(
                facId,
                "ACTIVE",
                "SUSPENDED",
                "SUSPEND",
                request.reason(),
                authentication.getName()
        );

        return get(facId);
    }

    @Transactional
    public BankCreditLineMonitoringResponse reactivate(
            Long facId,
            BankCreditLineStatusActionRequest request,
            Authentication authentication
    ) {
        Map<String, Object> facility = lockFacility(facId);

        if (!"SUSPENDED".equals(facility.get("cl_fac_sts"))) {
            throw conflict(
                    "Only SUSPENDED facilities can be reactivated"
            );
        }

        LocalDate today = LocalDate.now();

        LocalDate effectiveDate =
                ((Date) facility.get("eff_dt")).toLocalDate();

        LocalDate expiryDate =
                ((Date) facility.get("expiry_dt")).toLocalDate();

        if (today.isBefore(effectiveDate)
                || !today.isBefore(expiryDate)) {
            throw conflict(
                    "Facility is outside its effective period"
            );
        }

        BigDecimal approved =
                (BigDecimal) facility.get("appr_limit");

        BigDecimal utilized =
                (BigDecimal) facility.get("utilized_amt");

        if (approved.signum() <= 0
                || utilized.signum() < 0
                || utilized.compareTo(approved) > 0) {
            throw conflict("Invalid facility limit or utilization");
        }

        // Revalidate customer, KYC, identification,
        // product, scheme and rate before reactivation.
        eligibilityGuard.validateForLimitAdjustment(
                facility,
                today
        );

        changeStatus(
                facId,
                "SUSPENDED",
                "ACTIVE",
                "REACTIVATE",
                request.reason(),
                authentication.getName()
        );

        return get(facId);
    }

    @Transactional
    public BankCreditLineMonitoringResponse close(
            Long facId,
            BankCreditLineStatusActionRequest request,
            Authentication authentication
    ) {
        Map<String, Object> facility = lockFacility(facId);

        String currentStatus = (String) facility.get("cl_fac_sts");

        if (!"ACTIVE".equals(currentStatus)
                && !"SUSPENDED".equals(currentStatus)) {
            throw conflict(
                    "Only ACTIVE or SUSPENDED facilities can be closed"
            );
        }

        BigDecimal utilized =
                (BigDecimal) facility.get("utilized_amt");

        if (utilized.compareTo(BigDecimal.ZERO) != 0) {
            throw conflict(
                    "Facility cannot close with outstanding principal"
            );
        }

        BigDecimal accruedInterest = getAccruedInterest(facId);

        if (accruedInterest.compareTo(BigDecimal.ZERO) != 0) {
            throw conflict(
                    "Facility cannot close with unsettled accrued interest"
            );
        }

        Integer openStocks = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_floor_stock
                        WHERE cl_fac_id = ?
                          AND stock_sts = 'FINANCED'
                        """,
                Integer.class,
                facId
        );

        if (openStocks != null && openStocks > 0) {
            throw conflict(
                    "Facility has unsettled floor stock vehicles"
            );
        }

        changeStatus(
                facId,
                currentStatus,
                "CLOSED",
                "CLOSE",
                request.reason(),
                authentication.getName()
        );

        return get(facId);
    }

    @Transactional(readOnly = true)
    public List<BankCreditLineStatusHistoryResponse> history(
            Long facId
    ) {
        ensureFacilityExists(facId);

        return jdbc.query(
                """
                        SELECT *
                        FROM public.bank_credit_line_status_history
                        WHERE cl_fac_id = ?
                        ORDER BY hist_id DESC
                        """,
                (rs, rowNum) ->
                        new BankCreditLineStatusHistoryResponse(
                                rs.getLong("hist_id"),
                                rs.getLong("cl_fac_id"),
                                rs.getString("action_typ"),
                                rs.getString("old_sts"),
                                rs.getString("new_sts"),
                                rs.getString("reason"),
                                rs.getObject(
                                        "action_dt",
                                        OffsetDateTime.class
                                ),
                                rs.getString("action_by")
                        ),
                facId
        );
    }

    private void changeStatus(
            Long facId,
            String oldStatus,
            String newStatus,
            String actionType,
            String reason,
            String username
    ) {
        int updated = jdbc.update(
                """
                        UPDATE public.bank_credit_line_facility
                        SET cl_fac_sts = ?,
                            upd_dt = CURRENT_TIMESTAMP,
                            upd_by = ?
                        WHERE cl_fac_id = ?
                          AND cl_fac_sts = ?
                        """,
                newStatus,
                username,
                facId,
                oldStatus
        );

        if (updated != 1) {
            throw conflict("Facility status transition failed");
        }

        jdbc.update(
                """
                        INSERT INTO public.bank_credit_line_status_history
                        (
                            cl_fac_id,
                            action_typ,
                            old_sts,
                            new_sts,
                            reason,
                            action_by
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                facId,
                actionType,
                oldStatus,
                newStatus,
                reason.trim(),
                username
        );
    }

    private String monitoringSql() {
        return """
                SELECT
                    f.cl_fac_id,
                    f.cl_fac_ref,
                    f.cust_id,
                    f.cl_fac_typ,
                    f.cl_fac_sts,
                    f.ccy_cd,
                    f.appr_limit,
                    f.utilized_amt,
                    f.available_limit,
                    f.eff_dt,
                    f.expiry_dt,
                    COALESCE(
                        (
                            SELECT SUM(a.daily_interest_amt)
                            FROM public.bank_credit_line_interest_accrual a
                            WHERE a.cl_fac_id = f.cl_fac_id
                        ),
                        0
                    ) AS accrued_interest,
                    (
                        SELECT COUNT(*)
                        FROM public.bank_floor_stock s
                        WHERE s.cl_fac_id = f.cl_fac_id
                          AND s.stock_sts = 'FINANCED'
                    ) AS open_stocks
                FROM public.bank_credit_line_facility f
                """;
    }

    private BankCreditLineMonitoringResponse mapMonitoring(
            ResultSet rs
    ) throws SQLException {

        LocalDate today = LocalDate.now();

        LocalDate effectiveDate =
                rs.getDate("eff_dt").toLocalDate();

        LocalDate expiryDate =
                rs.getDate("expiry_dt").toLocalDate();

        BigDecimal utilized =
                rs.getBigDecimal("utilized_amt");

        BigDecimal accruedInterest =
                rs.getBigDecimal("accrued_interest");

        long openStocks = rs.getLong("open_stocks");

        String status = rs.getString("cl_fac_sts");

        boolean eligibleForClosure =
                ("ACTIVE".equals(status)
                        || "SUSPENDED".equals(status))
                        && utilized.signum() == 0
                        && accruedInterest.signum() == 0
                        && openStocks == 0;

        return new BankCreditLineMonitoringResponse(
                rs.getLong("cl_fac_id"),
                rs.getString("cl_fac_ref"),
                rs.getLong("cust_id"),
                rs.getString("cl_fac_typ"),
                status,
                rs.getString("ccy_cd"),
                rs.getBigDecimal("appr_limit"),
                utilized,
                rs.getBigDecimal("available_limit"),
                accruedInterest.setScale(
                        2,
                        RoundingMode.HALF_UP
                ),
                effectiveDate,
                expiryDate,
                ChronoUnit.DAYS.between(today, expiryDate),
                !today.isBefore(expiryDate),
                eligibleForClosure
        );
    }

    private BigDecimal getAccruedInterest(Long facId) {
        BigDecimal total = jdbc.queryForObject(
                """
                        SELECT COALESCE(
                            SUM(daily_interest_amt),
                            0
                        )
                        FROM public.bank_credit_line_interest_accrual
                        WHERE cl_fac_id = ?
                        """,
                BigDecimal.class,
                facId
        );

        return total == null ? BigDecimal.ZERO : total;
    }

    private Map<String, Object> lockFacility(Long facId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT *
                        FROM public.bank_credit_line_facility
                        WHERE cl_fac_id = ?
                        FOR UPDATE
                        """,
                facId
        );

        if (rows.isEmpty()) {
            throw notFound("Credit line facility not found");
        }

        return rows.get(0);
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
