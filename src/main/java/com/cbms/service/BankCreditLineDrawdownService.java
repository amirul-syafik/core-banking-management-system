package com.cbms.service;

import com.cbms.dto.BankCreditLineDrawdownRequest;
import com.cbms.dto.BankCreditLineDrawdownResponse;

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
public class BankCreditLineDrawdownService {

    private final JdbcTemplate jdbc;

    public BankCreditLineDrawdownService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public BankCreditLineDrawdownResponse drawdown(
            Long facId,
            BankCreditLineDrawdownRequest request,
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
                    "Only revolving credit facilities support this drawdown"
            );
        }

        if (!"MYR".equals(facility.get("ccy_cd"))) {
            throw conflict("Only MYR drawdowns are supported");
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

        BigDecimal availableLimit =
                approvedLimit.subtract(utilizedAmount);

        BigDecimal drawAmount = request.drawAmount();

        if (drawAmount.compareTo(availableLimit) > 0) {
            throw conflict("Insufficient available credit limit");
        }

        String drawReference = request.drawReference().trim();

        Integer duplicateCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_credit_line_drawdown
                        WHERE draw_ref = ?
                        """,
                Integer.class,
                drawReference
        );

        if (duplicateCount != null && duplicateCount > 0) {
            throw conflict("Drawdown reference already exists");
        }

        BigDecimal newUtilizedAmount =
                utilizedAmount.add(drawAmount);

        int updated = jdbc.update(
                """
                        UPDATE public.bank_credit_line_facility
                        SET utilized_amt = ?,
                            upd_dt = CURRENT_TIMESTAMP,
                            upd_by = ?
                        WHERE cl_fac_id = ?
                          AND cl_fac_sts = 'ACTIVE'
                          AND utilized_amt + ? <= appr_limit
                        """,
                newUtilizedAmount,
                authentication.getName(),
                facId,
                drawAmount
        );

        if (updated != 1) {
            throw conflict("Unable to update facility utilization");
        }

        try {
            return jdbc.queryForObject(
                    """
                            INSERT INTO public.bank_credit_line_drawdown
                            (
                                draw_ref,
                                cl_fac_id,
                                draw_amt,
                                ccy_cd,
                                draw_sts,
                                remarks,
                                crt_by
                            )
                            VALUES (?, ?, ?, 'MYR', 'POSTED', ?, ?)
                            RETURNING
                                draw_id,
                                draw_ref,
                                cl_fac_id,
                                draw_amt,
                                ccy_cd,
                                draw_sts,
                                crt_dt,
                                crt_by
                            """,
                    (rs, rowNum) -> new BankCreditLineDrawdownResponse(
                            rs.getLong("draw_id"),
                            rs.getString("draw_ref"),
                            rs.getLong("cl_fac_id"),
                            rs.getBigDecimal("draw_amt"),
                            rs.getString("ccy_cd"),
                            rs.getString("draw_sts"),
                            newUtilizedAmount,
                            approvedLimit.subtract(newUtilizedAmount),
                            rs.getObject(
                                    "crt_dt",
                                    OffsetDateTime.class
                            ),
                            rs.getString("crt_by")
                    ),
                    drawReference,
                    facId,
                    drawAmount,
                    request.remarks(),
                    authentication.getName()
            );

        } catch (DuplicateKeyException ex) {
            throw conflict("Drawdown reference already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<BankCreditLineDrawdownResponse> getDrawdowns(
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

        BigDecimal utilizedAmount =
                (BigDecimal) facility.get("utilized_amt");

        BigDecimal approvedLimit =
                (BigDecimal) facility.get("appr_limit");

        return jdbc.query(
                """
                        SELECT *
                        FROM public.bank_credit_line_drawdown
                        WHERE cl_fac_id = ?
                        ORDER BY draw_id DESC
                        """,
                (rs, rowNum) -> new BankCreditLineDrawdownResponse(
                        rs.getLong("draw_id"),
                        rs.getString("draw_ref"),
                        rs.getLong("cl_fac_id"),
                        rs.getBigDecimal("draw_amt"),
                        rs.getString("ccy_cd"),
                        rs.getString("draw_sts"),
                        utilizedAmount,
                        approvedLimit.subtract(utilizedAmount),
                        rs.getObject("crt_dt", OffsetDateTime.class),
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
