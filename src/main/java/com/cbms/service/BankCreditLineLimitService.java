
package com.cbms.service;

import com.cbms.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class BankCreditLineLimitService {

    private final JdbcTemplate jdbc;
    private final BankCreditLineEligibilityGuard eligibilityGuard;

    public BankCreditLineLimitService(
            JdbcTemplate jdbc,
            BankCreditLineEligibilityGuard eligibilityGuard
    ) {
        this.jdbc = jdbc;
        this.eligibilityGuard = eligibilityGuard;
    }

    @Transactional
    public BankCreditLineLimitResponse activate(
            Long facId,
            BankCreditLineActivationRequest request,
            Authentication authentication
    ) {
        Map<String, Object> facility = lockFacility(facId);

        if (!"DRAFT".equals(facility.get("cl_fac_sts"))) {
            throw conflict("Only DRAFT facilities can be activated");
        }

        LocalDate today = LocalDate.now();

        LocalDate effectiveDate = date(facility, "eff_dt");
        LocalDate expiryDate = date(facility, "expiry_dt");

        if (today.isBefore(effectiveDate)
                || !today.isBefore(expiryDate)) {
            throw conflict(
                    "Facility is outside its effective date range"
            );
        }

        BigDecimal approvedLimit = amount(facility, "appr_limit");
        BigDecimal utilized = amount(facility, "utilized_amt");

        if (approvedLimit.signum() <= 0 || utilized.signum() != 0) {
            throw conflict("Invalid initial credit line balances");
        }

        // Validate product, scheme, customer and KYC before activation.
        eligibilityGuard.validateForActivation(facility, today);

        int updated = jdbc.update(
                """
                        UPDATE public.bank_credit_line_facility
                        SET cl_fac_sts = 'ACTIVE',
                            upd_dt = CURRENT_TIMESTAMP,
                            upd_by = ?
                        WHERE cl_fac_id = ?
                          AND cl_fac_sts = 'DRAFT'
                        """,
                authentication.getName(),
                facId
        );

        if (updated != 1) {
            throw conflict("Facility activation failed");
        }

        insertHistory(
                facId,
                "ACTIVATE",
                approvedLimit,
                approvedLimit,
                utilized,
                request.reason(),
                authentication.getName()
        );

        return getLimitInternal(facId);
    }

    @Transactional
    public BankCreditLineLimitResponse adjustLimit(
            Long facId,
            BankCreditLineLimitAdjustmentRequest request,
            Authentication authentication
    ) {
        Map<String, Object> facility = lockFacility(facId);

        if (!"ACTIVE".equals(facility.get("cl_fac_sts"))) {
            throw conflict("Only ACTIVE facilities allow limit adjustments");
        }

        LocalDate today = LocalDate.now();

        if (today.isBefore(date(facility, "eff_dt"))
                || !today.isBefore(date(facility, "expiry_dt"))) {
            throw conflict("Facility is outside its effective date range");
        }

        BigDecimal oldLimit = amount(facility, "appr_limit");
        BigDecimal newLimit = request.newLimit();
        BigDecimal utilized = amount(facility, "utilized_amt");

        if (newLimit.compareTo(oldLimit) == 0) {
            throw conflict("New limit must differ from existing limit");
        }

        if (newLimit.compareTo(utilized) < 0) {
            throw conflict(
                    "New limit cannot be lower than utilized amount"
            );
        }

        // Recheck scheme rules before changing the limit.
        eligibilityGuard.validateForLimitAdjustment(facility, today);

        BigDecimal minAmount = (BigDecimal) facility.get("min_amt");
        BigDecimal maxAmount = (BigDecimal) facility.get("max_amt");

        if (minAmount != null && newLimit.compareTo(minAmount) < 0) {
            throw conflict("New limit is below scheme minimum");
        }

        if (maxAmount != null && newLimit.compareTo(maxAmount) > 0) {
            throw conflict("New limit exceeds scheme maximum");
        }

        String action = newLimit.compareTo(oldLimit) > 0
                ? "LIMIT_INCREASE"
                : "LIMIT_DECREASE";

        int updated = jdbc.update(
                """
                        UPDATE public.bank_credit_line_facility
                        SET appr_limit = ?,
                            upd_dt = CURRENT_TIMESTAMP,
                            upd_by = ?
                        WHERE cl_fac_id = ?
                          AND cl_fac_sts = 'ACTIVE'
                        """,
                newLimit,
                authentication.getName(),
                facId
        );

        if (updated != 1) {
            throw conflict("Limit adjustment failed");
        }

        insertHistory(
                facId,
                action,
                oldLimit,
                newLimit,
                utilized,
                request.reason(),
                authentication.getName()
        );

        return getLimitInternal(facId);
    }

    @Transactional(readOnly = true)
    public BankCreditLineLimitResponse getLimit(Long facId) {
        return getLimitInternal(facId);
    }

    @Transactional(readOnly = true)
    public List<BankCreditLineLimitHistoryResponse> getHistory(Long facId) {
        getLimitInternal(facId);

        return jdbc.query(
                """
                        SELECT *
                        FROM public.bank_credit_line_limit_history
                        WHERE cl_fac_id = ?
                        ORDER BY hist_id DESC
                        """,
                (rs, rowNum) -> new BankCreditLineLimitHistoryResponse(
                        rs.getLong("hist_id"),
                        rs.getLong("cl_fac_id"),
                        rs.getString("action_typ"),
                        rs.getBigDecimal("old_limit"),
                        rs.getBigDecimal("new_limit"),
                        rs.getBigDecimal("utilized_amt"),
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

    private Map<String, Object> lockFacility(Long facId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT
                            f.*,
                            s.min_amt,
                            s.max_amt
                        FROM public.bank_credit_line_facility f
                        JOIN public.bank_scheme s
                            ON s.schm_id = f.schm_id
                        WHERE f.cl_fac_id = ?
                        FOR UPDATE OF f
                        """,
                facId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Credit line facility not found"
            );
        }

        return rows.get(0);
    }

    private BankCreditLineLimitResponse getLimitInternal(Long facId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT
                            cl_fac_id,
                            cl_fac_ref,
                            cl_fac_typ,
                            cl_fac_sts,
                            ccy_cd,
                            appr_limit,
                            utilized_amt,
                            available_limit,
                            eff_dt,
                            expiry_dt
                        FROM public.bank_credit_line_facility
                        WHERE cl_fac_id = ?
                        """,
                facId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Credit line facility not found"
            );
        }

        Map<String, Object> row = rows.get(0);

        return new BankCreditLineLimitResponse(
                ((Number) row.get("cl_fac_id")).longValue(),
                (String) row.get("cl_fac_ref"),
                (String) row.get("cl_fac_typ"),
                (String) row.get("cl_fac_sts"),
                (String) row.get("ccy_cd"),
                amount(row, "appr_limit"),
                amount(row, "utilized_amt"),
                amount(row, "available_limit"),
                date(row, "eff_dt"),
                date(row, "expiry_dt")
        );
    }

    private void insertHistory(
            Long facId,
            String action,
            BigDecimal oldLimit,
            BigDecimal newLimit,
            BigDecimal utilized,
            String reason,
            String username
    ) {
        jdbc.update(
                """
                        INSERT INTO public.bank_credit_line_limit_history
                        (
                            cl_fac_id,
                            action_typ,
                            old_limit,
                            new_limit,
                            utilized_amt,
                            reason,
                            action_by
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                facId,
                action,
                oldLimit,
                newLimit,
                utilized,
                reason,
                username
        );
    }

    private BigDecimal amount(Map<String, Object> row, String key) {
        return (BigDecimal) row.get(key);
    }

    private LocalDate date(Map<String, Object> row, String key) {
        Object value = row.get(key);

        if (value instanceof LocalDate localDate) {
            return localDate;
        }

        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        throw new IllegalStateException("Invalid date column: " + key);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
