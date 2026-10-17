package com.cbms.service;

import com.cbms.dto.BankFinancingOutstandingResponse;
import com.cbms.dto.BankFinancingSummaryResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class BankFinancingOutstandingService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final JdbcTemplate jdbc;

    public BankFinancingOutstandingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public BankFinancingOutstandingResponse getOutstanding(Long facId) {

        Map<String, Object> facility = getFacility(facId);

        BigDecimal approvedAmount = bd(facility, "appr_amt");

        BigDecimal disbursedPrincipal = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(disb_amt), 0)
                FROM public.bank_financing_disbursement
                WHERE fac_id = ?
                  AND disb_sts = 'POSTED'
                """,
                BigDecimal.class,
                facId
        );

        Map<String, Object> totals = jdbc.queryForMap(
                """
                SELECT
                    COUNT(*) AS total_instalments,

                    COALESCE(SUM(principal_amt), 0)
                        AS scheduled_principal,

                    COALESCE(SUM(interest_amt), 0)
                        AS scheduled_interest,

                    COALESCE(SUM(paid_principal), 0)
                        AS principal_paid,

                    COALESCE(SUM(paid_interest), 0)
                        AS interest_paid,

                    COUNT(*) FILTER (
                        WHERE inst_sts = 'PAID'
                    ) AS paid_instalments,

                    COUNT(*) FILTER (
                        WHERE inst_sts = 'PARTIAL'
                    ) AS partial_instalments,

                    COUNT(*) FILTER (
                        WHERE inst_sts = 'PENDING'
                    ) AS pending_instalments,

                    COUNT(*) FILTER (
                        WHERE due_dt < CURRENT_DATE
                          AND (
                              paid_principal < principal_amt
                              OR paid_interest < interest_amt
                          )
                    ) AS overdue_instalments,

                    COALESCE(SUM(
                        CASE WHEN due_dt < CURRENT_DATE
                        THEN principal_amt - paid_principal
                        ELSE 0 END
                    ), 0) AS overdue_principal,

                    COALESCE(SUM(
                        CASE WHEN due_dt < CURRENT_DATE
                        THEN interest_amt - paid_interest
                        ELSE 0 END
                    ), 0) AS overdue_interest

                FROM public.bank_financing_repayment_schedule
                WHERE fac_id = ?
                """,
                facId
        );

        BigDecimal scheduledPrincipal = bd(totals, "scheduled_principal");
        BigDecimal scheduledInterest = bd(totals, "scheduled_interest");

        BigDecimal principalPaid = bd(totals, "principal_paid");
        BigDecimal interestPaid = bd(totals, "interest_paid");

        BigDecimal principalOutstanding =
                disbursedPrincipal.subtract(principalPaid);

        BigDecimal remainingInterest =
                scheduledInterest.subtract(interestPaid);

        BigDecimal scheduledRepayment =
                scheduledPrincipal.add(scheduledInterest);

        BigDecimal totalPaid = principalPaid.add(interestPaid);

        BigDecimal totalUnpaid = scheduledRepayment.subtract(totalPaid);

        BigDecimal overduePrincipal = bd(totals, "overdue_principal");
        BigDecimal overdueInterest = bd(totals, "overdue_interest");

        BigDecimal overdueAmount =
                overduePrincipal.add(overdueInterest);

        BigDecimal progress = BigDecimal.ZERO;

        if (scheduledRepayment.signum() > 0) {
            progress = totalPaid
                    .multiply(BigDecimal.valueOf(100))
                    .divide(scheduledRepayment, 2, RoundingMode.HALF_UP);
        }

        List<Map<String, Object>> nextRows = jdbc.queryForList(
                """
                SELECT
                    inst_no,
                    due_dt,
                    (principal_amt - paid_principal)
                      + (interest_amt - paid_interest)
                        AS due_amount
                FROM public.bank_financing_repayment_schedule
                WHERE fac_id = ?
                  AND (
                      paid_principal < principal_amt
                      OR paid_interest < interest_amt
                  )
                ORDER BY inst_no
                LIMIT 1
                """,
                facId
        );

        Integer nextInstalmentNo = null;
        LocalDate nextDueDate = null;
        BigDecimal nextDueAmount = ZERO;

        if (!nextRows.isEmpty()) {
            Map<String, Object> next = nextRows.get(0);

            nextInstalmentNo =
                    ((Number) next.get("inst_no")).intValue();

            nextDueDate = localDate(next.get("due_dt"));
            nextDueAmount = bd(next, "due_amount");
        }

        return new BankFinancingOutstandingResponse(
                facId,
                (String) facility.get("fac_ref"),
                (String) facility.get("fac_sts"),
                (String) facility.get("ccy_cd"),

                approvedAmount,
                disbursedPrincipal,
                principalPaid,
                principalOutstanding,

                scheduledInterest,
                interestPaid,
                remainingInterest,

                scheduledRepayment,
                totalPaid,
                totalUnpaid,

                overdueAmount,
                overduePrincipal,
                overdueInterest,

                count(totals, "total_instalments"),
                count(totals, "paid_instalments"),
                count(totals, "partial_instalments"),
                count(totals, "pending_instalments"),
                count(totals, "overdue_instalments"),

                nextInstalmentNo,
                nextDueDate,
                nextDueAmount,

                progress,
                localDate(facility.get("maturity_dt"))
        );
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public BankFinancingSummaryResponse getSummary(Long facId) {

        Map<String, Object> facility = getFacility(facId);

        List<Map<String, Object>> disbursements = jdbc.queryForList(
                """
                SELECT disb_sts, disb_amt
                FROM public.bank_financing_disbursement
                WHERE fac_id = ?
                """,
                facId
        );

        String disbursementStatus = "NOT_DISBURSED";
        BigDecimal disbursementAmount = ZERO;

        if (!disbursements.isEmpty()) {
            Map<String, Object> disbursement = disbursements.get(0);

            disbursementStatus =
                    (String) disbursement.get("disb_sts");

            disbursementAmount =
                    bd(disbursement, "disb_amt");
        }

        String bankingType = jdbc.queryForObject(
                """
                SELECT banking_typ
                FROM public.bank_product
                WHERE prod_id = ?
                """,
                String.class,
                facility.get("prod_id")
        );

        return new BankFinancingSummaryResponse(
                facId,
                (String) facility.get("fac_ref"),
                (String) facility.get("fac_sts"),

                longValue(facility, "appl_id"),
                longValue(facility, "cust_id"),
                longValue(facility, "prod_id"),
                longValue(facility, "schm_id"),

                (String) facility.get("fac_typ"),
                bankingType,
                (String) facility.get("ccy_cd"),

                bd(facility, "appr_amt"),
                count(facility, "tenor_mth"),
                (String) facility.get("rate_typ"),
                bd(facility, "eff_rate"),

                localDate(facility.get("appr_dt")),
                localDate(facility.get("book_dt")),
                localDate(facility.get("maturity_dt")),

                disbursementStatus,
                disbursementAmount,

                getOutstanding(facId)
        );
    }

    private Map<String, Object> getFacility(Long facId) {

        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT
                    fac_id, fac_ref, fac_sts,
                    appl_id, cust_id, prod_id, schm_id,
                    fac_typ, ccy_cd,
                    appr_amt, tenor_mth,
                    rate_typ, eff_rate,
                    appr_dt, book_dt, maturity_dt
                FROM public.bank_financing_facility
                WHERE fac_id = ?
                """,
                facId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Financing facility not found"
            );
        }

        return rows.get(0);
    }

    private BigDecimal bd(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? ZERO : (BigDecimal) value;
    }

    private Integer count(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).intValue();
    }

    private Long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).longValue();
    }

    private LocalDate localDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        if (value instanceof Date date) return date.toLocalDate();

        throw new IllegalStateException(
                "Unexpected SQL date type: " + value.getClass()
        );
    }
}
