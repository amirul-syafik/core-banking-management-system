package com.cbms.service;

import com.cbms.dto.BankCreditLineRepaymentRequest;
import com.cbms.dto.BankCreditLineRepaymentResponse;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class BankCreditLineRepaymentService {

    private final JdbcTemplate jdbc;

    public BankCreditLineRepaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public BankCreditLineRepaymentResponse repay(
            Long facId,
            BankCreditLineRepaymentRequest request,
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
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Credit line facility not found"
            );
        }

        Map<String, Object> facility = facilities.get(0);

        if (!"ACTIVE".equals(facility.get("cl_fac_sts"))) {
            throw conflict("Facility must be ACTIVE");
        }

        if (!"REVOLVING_CREDIT".equals(facility.get("cl_fac_typ"))) {
            throw conflict(
                    "Only revolving credit facilities support this repayment"
            );
        }

        if (!"MYR".equals(facility.get("ccy_cd"))) {
            throw conflict("Only MYR repayments are supported");
        }

        LocalDate today = LocalDate.now();

        LocalDate effectiveDate =
                ((Date) facility.get("eff_dt")).toLocalDate();

        LocalDate expiryDate =
                ((Date) facility.get("expiry_dt")).toLocalDate();

        if (today.isBefore(effectiveDate)
                || !today.isBefore(expiryDate)) {
            throw conflict("Facility is outside its effective period");
        }

        BigDecimal approvedLimit =
                (BigDecimal) facility.get("appr_limit");

        BigDecimal utilizedAmount =
                (BigDecimal) facility.get("utilized_amt");

        BigDecimal repayAmount = request.repayAmount();

        if (repayAmount == null || repayAmount.signum() <= 0) {
            throw conflict("Repayment amount must be positive");
        }

        if (utilizedAmount.signum() <= 0) {
            throw conflict("Facility has no outstanding principal");
        }

        if (repayAmount.compareTo(utilizedAmount) > 0) {
            throw conflict(
                    "Repayment amount exceeds outstanding principal"
            );
        }

        String repayReference = request.repayReference().trim();

        if (repayReference.isEmpty()) {
            throw conflict("Repayment reference is required");
        }

        Integer duplicateCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_credit_line_repayment
                        WHERE repay_ref = ?
                        """,
                Integer.class,
                repayReference
        );

        if (duplicateCount != null && duplicateCount > 0) {
            throw conflict("Repayment reference already exists");
        }

        BigDecimal newUtilizedAmount =
                utilizedAmount.subtract(repayAmount);

        int updated = jdbc.update(
                """
                        UPDATE public.bank_credit_line_facility
                        SET utilized_amt = utilized_amt - ?,
                            upd_dt = CURRENT_TIMESTAMP,
                            upd_by = ?
                        WHERE cl_fac_id = ?
                          AND cl_fac_sts = 'ACTIVE'
                          AND utilized_amt >= ?
                        """,
                repayAmount,
                authentication.getName(),
                facId,
                repayAmount
        );

        if (updated != 1) {
            throw conflict("Unable to update facility utilization");
        }

        try {
            return jdbc.queryForObject(
                    """
                            INSERT INTO public.bank_credit_line_repayment
                            (
                                repay_ref,
                                cl_fac_id,
                                repay_amt,
                                principal_amt,
                                ccy_cd,
                                repay_sts,
                                remarks,
                                crt_by
                            )
                            VALUES (?, ?, ?, ?, 'MYR', 'POSTED', ?, ?)
                            RETURNING
                                repay_id,
                                repay_ref,
                                cl_fac_id,
                                repay_amt,
                                principal_amt,
                                ccy_cd,
                                repay_sts,
                                crt_dt,
                                crt_by
                            """,
                    (rs, rowNum) -> new BankCreditLineRepaymentResponse(
                            rs.getLong("repay_id"),
                            rs.getString("repay_ref"),
                            rs.getLong("cl_fac_id"),
                            rs.getBigDecimal("repay_amt"),
                            rs.getBigDecimal("principal_amt"),
                            rs.getString("ccy_cd"),
                            rs.getString("repay_sts"),
                            newUtilizedAmount,
                            approvedLimit.subtract(newUtilizedAmount),
                            rs.getObject(
                                    "crt_dt",
                                    OffsetDateTime.class
                            ),
                            rs.getString("crt_by")
                    ),
                    repayReference,
                    facId,
                    repayAmount,
                    repayAmount,
                    request.remarks(),
                    authentication.getName()
            );

        } catch (DuplicateKeyException ex) {
            throw conflict("Repayment reference already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<BankCreditLineRepaymentResponse> getRepayments(
            Long facId
    ) {
        List<Map<String, Object>> facilities = jdbc.queryForList(
                """
                        SELECT appr_limit, utilized_amt
                        FROM public.bank_credit_line_facility
                        WHERE cl_fac_id = ?
                        """,
                facId
        );

        if (facilities.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Credit line facility not found"
            );
        }

        Map<String, Object> facility = facilities.get(0);

        BigDecimal approvedLimit =
                (BigDecimal) facility.get("appr_limit");

        BigDecimal utilizedAmount =
                (BigDecimal) facility.get("utilized_amt");

        return jdbc.query(
                """
                        SELECT *
                        FROM public.bank_credit_line_repayment
                        WHERE cl_fac_id = ?
                        ORDER BY repay_id DESC
                        """,
                (rs, rowNum) -> new BankCreditLineRepaymentResponse(
                        rs.getLong("repay_id"),
                        rs.getString("repay_ref"),
                        rs.getLong("cl_fac_id"),
                        rs.getBigDecimal("repay_amt"),
                        rs.getBigDecimal("principal_amt"),
                        rs.getString("ccy_cd"),
                        rs.getString("repay_sts"),
                        utilizedAmount,
                        approvedLimit.subtract(utilizedAmount),
                        rs.getObject(
                                "crt_dt",
                                OffsetDateTime.class
                        ),
                        rs.getString("crt_by")
                ),
                facId
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
