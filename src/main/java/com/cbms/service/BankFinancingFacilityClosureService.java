package com.cbms.service;

import com.cbms.dto.BankFinancingFacilityClosureRequest;
import com.cbms.dto.BankFinancingFacilityClosureResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BankFinancingFacilityClosureService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final JdbcTemplate jdbc;

    public BankFinancingFacilityClosureService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public BankFinancingFacilityClosureResponse close(
            Long facId,
            BankFinancingFacilityClosureRequest request,
            Authentication authentication
    ) {
        // Same facility lock used by repayment and disbursement.
        List<Map<String, Object>> facilities = jdbc.queryForList(
                """
                        SELECT fac_id, fac_sts, appr_amt, tenor_mth
                        FROM public.bank_financing_facility
                        WHERE fac_id = ?
                        FOR UPDATE
                        """,
                facId
        );

        if (facilities.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Financing facility not found"
            );
        }

        Map<String, Object> facility = facilities.get(0);
        String status = (String) facility.get("fac_sts");

        // Safe retry: return the original closure.
        if ("CLOSED".equals(status)) {
            List<Map<String, Object>> existing = jdbc.queryForList(
                    """
                            SELECT *
                            FROM public.bank_financing_facility_closure
                            WHERE fac_id = ?
                            """,
                    facId
            );

            if (existing.size() != 1) {
                throw conflict(
                        "Facility is CLOSED but closure audit is missing"
                );
            }

            return mapResponse(existing.get(0));
        }

        if (!"ACTIVE".equals(status)) {
            throw conflict("Only ACTIVE facilities can be closed");
        }

        Integer disbursements = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_financing_disbursement
                        WHERE fac_id = ?
                          AND disb_sts = 'POSTED'
                          AND disb_amt = ?
                        """,
                Integer.class,
                facId,
                facility.get("appr_amt")
        );

        if (disbursements == null || disbursements != 1) {
            throw conflict(
                    "Facility must have one fully posted disbursement"
            );
        }

        Integer unresolvedDisbursements = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_financing_disbursement
                        WHERE fac_id = ?
                          AND disb_sts <> 'POSTED'
                        """,
                Integer.class,
                facId
        );

        if (unresolvedDisbursements != null
                && unresolvedDisbursements > 0) {
            throw conflict("Unresolved disbursement exists");
        }

        Map<String, Object> schedule = jdbc.queryForMap(
                """
                        SELECT
                            COUNT(*) AS inst_count,
                            COALESCE(SUM(principal_amt), 0)
                                AS scheduled_principal,
                            COALESCE(SUM(interest_amt), 0)
                                AS scheduled_interest,
                            COALESCE(SUM(paid_principal), 0)
                                AS paid_principal,
                            COALESCE(SUM(paid_interest), 0)
                                AS paid_interest,
                            COUNT(*) FILTER (
                                WHERE inst_sts <> 'PAID'
                                   OR paid_principal <> principal_amt
                                   OR paid_interest <> interest_amt
                            ) AS unpaid_count
                        FROM public.bank_financing_repayment_schedule
                        WHERE fac_id = ?
                        """,
                facId
        );

        int expectedTenor =
                ((Number) facility.get("tenor_mth")).intValue();

        if (expectedTenor <= 0
                || number(schedule, "inst_count") != expectedTenor) {
            throw conflict("Repayment schedule is incomplete");
        }

        BigDecimal scheduledPrincipal =
                amount(schedule, "scheduled_principal");
        BigDecimal scheduledInterest =
                amount(schedule, "scheduled_interest");

        BigDecimal principalPaid =
                amount(schedule, "paid_principal");
        BigDecimal interestPaid =
                amount(schedule, "paid_interest");

        BigDecimal principalOutstanding =
                scheduledPrincipal.subtract(principalPaid);

        BigDecimal interestOutstanding =
                scheduledInterest.subtract(interestPaid);

        if (scheduledPrincipal.compareTo(
                (BigDecimal) facility.get("appr_amt")) != 0) {
            throw conflict(
                    "Scheduled principal does not match facility amount"
            );
        }

        if (number(schedule, "unpaid_count") != 0
                || principalOutstanding.signum() != 0
                || interestOutstanding.signum() != 0) {
            throw conflict(
                    "Facility has outstanding instalment balances"
            );
        }

        // Verify every repayment is POSTED and fully allocated.
        Integer invalidRepayments = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM (
                            SELECT
                                r.repay_id,
                                r.repay_amt,
                                r.repay_sts,
                                COALESCE(SUM(
                                    a.principal_amt + a.interest_amt
                                ), 0) AS allocated
                            FROM public.bank_financing_repayment r
                            LEFT JOIN public.bank_financing_repayment_allocation a
                                ON a.repay_id = r.repay_id
                            WHERE r.fac_id = ?
                            GROUP BY r.repay_id
                        ) x
                        WHERE x.repay_sts <> 'POSTED'
                           OR x.repay_amt <> x.allocated
                        """,
                Integer.class,
                facId
        );

        if (invalidRepayments == null || invalidRepayments != 0) {
            throw conflict("Repayment allocation reconciliation failed");
        }

        // Verify allocations belong to this facility and reconcile
        // with the schedule's accumulated paid amounts.
        Map<String, Object> allocationTotals = jdbc.queryForMap(
                """
                        SELECT
                            COALESCE(SUM(a.principal_amt), 0)
                                AS allocated_principal,
                            COALESCE(SUM(a.interest_amt), 0)
                                AS allocated_interest,
                            COUNT(*) FILTER (
                                WHERE s.fac_id <> r.fac_id
                            ) AS wrong_facility_count
                        FROM public.bank_financing_repayment_allocation a
                        JOIN public.bank_financing_repayment r
                            ON r.repay_id = a.repay_id
                        JOIN public.bank_financing_repayment_schedule s
                            ON s.sched_id = a.sched_id
                        WHERE r.fac_id = ?
                        """,
                facId
        );

        if (number(allocationTotals, "wrong_facility_count") != 0
                || amount(allocationTotals, "allocated_principal")
                .compareTo(principalPaid) != 0
                || amount(allocationTotals, "allocated_interest")
                .compareTo(interestPaid) != 0) {
            throw conflict("Schedule and allocation totals do not reconcile");
        }

        // Reconcile every repayment journal independently.
        Integer invalidJournals = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM (
                            SELECT
                                r.repay_id,
                                r.repay_amt,
                                COALESCE(SUM(j.dr_amt), 0) AS dr,
                                COALESCE(SUM(j.cr_amt), 0) AS cr
                            FROM public.bank_financing_repayment r
                            LEFT JOIN public.bank_financing_repayment_journal j
                                ON j.repay_id = r.repay_id
                            WHERE r.fac_id = ?
                            GROUP BY r.repay_id
                        ) x
                        WHERE x.dr <> x.cr
                           OR x.dr <> x.repay_amt
                        """,
                Integer.class,
                facId
        );

        if (invalidJournals == null || invalidJournals != 0) {
            throw conflict("Repayment journal reconciliation failed");
        }

        // Confirm posted repayment total equals scheduled repayment.
        BigDecimal repaymentTotal = jdbc.queryForObject(
                """
                        SELECT COALESCE(SUM(repay_amt), 0)
                        FROM public.bank_financing_repayment
                        WHERE fac_id = ?
                          AND repay_sts = 'POSTED'
                        """,
                BigDecimal.class,
                facId
        );

        BigDecimal expectedTotal =
                scheduledPrincipal.add(scheduledInterest);

        if (repaymentTotal == null
                || repaymentTotal.compareTo(expectedTotal) != 0) {
            throw conflict("Total repayment amount does not reconcile");
        }

        // Validate the disbursement journal as well.
        Map<String, Object> disbJournal = jdbc.queryForMap(
                """
                        SELECT
                            COUNT(j.journal_id) AS line_count,
                            COALESCE(SUM(j.dr_amt), 0) AS dr,
                            COALESCE(SUM(j.cr_amt), 0) AS cr
                        FROM public.bank_financing_disbursement d
                        LEFT JOIN public.bank_financing_disbursement_journal j
                            ON j.disb_id = d.disb_id
                        WHERE d.fac_id = ?
                          AND d.disb_sts = 'POSTED'
                        """,
                facId
        );

        BigDecimal approvedAmount =
                (BigDecimal) facility.get("appr_amt");

        if (number(disbJournal, "line_count") != 2
                || amount(disbJournal, "dr")
                .compareTo(approvedAmount) != 0
                || amount(disbJournal, "cr")
                .compareTo(approvedAmount) != 0) {
            throw conflict("Disbursement journal reconciliation failed");
        }

        String username = authentication.getName();

        String closureRef = "CLOSE-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();

        Long closureId = jdbc.queryForObject(
                """
                        INSERT INTO public.bank_financing_facility_closure
                        (
                            fac_id, closure_ref, closure_typ,
                            principal_outstanding, interest_outstanding,
                            remarks, closed_by
                        )
                        VALUES (
                            ?, ?, 'FULL_REPAYMENT',
                            ?, ?, ?, ?
                        )
                        RETURNING closure_id
                        """,
                Long.class,
                facId,
                closureRef,
                ZERO,
                ZERO,
                request == null ? null : request.remarks(),
                username
        );

        jdbc.update(
                """
                        UPDATE public.bank_financing_facility
                        SET fac_sts = 'CLOSED',
                            closed_dt = CURRENT_TIMESTAMP,
                            closed_by = ?,
                            upd_dt = CURRENT_TIMESTAMP,
                            upd_by = ?
                        WHERE fac_id = ?
                        """,
                username,
                username,
                facId
        );

        Map<String, Object> saved = jdbc.queryForMap(
                """
                        SELECT *
                        FROM public.bank_financing_facility_closure
                        WHERE closure_id = ?
                        """,
                closureId
        );

        return mapResponse(saved);
    }

    @Transactional(readOnly = true)
    public BankFinancingFacilityClosureResponse getClosure(Long facId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT *
                        FROM public.bank_financing_facility_closure
                        WHERE fac_id = ?
                        """,
                facId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Facility closure record not found"
            );
        }

        return mapResponse(rows.get(0));
    }

    private BankFinancingFacilityClosureResponse mapResponse(
            Map<String, Object> row
    ) {
        Object date = row.get("closed_dt");
        OffsetDateTime closedDate;

        if (date instanceof OffsetDateTime odt) {
            closedDate = odt;
        } else if (date instanceof Timestamp timestamp) {
            closedDate = timestamp.toInstant().atOffset(ZoneOffset.UTC);
        } else {
            throw new IllegalStateException(
                    "Unexpected closure timestamp type"
            );
        }

        return new BankFinancingFacilityClosureResponse(
                ((Number) row.get("closure_id")).longValue(),
                ((Number) row.get("fac_id")).longValue(),
                (String) row.get("closure_ref"),
                (String) row.get("closure_typ"),
                "CLOSED",
                amount(row, "principal_outstanding"),
                amount(row, "interest_outstanding"),
                (String) row.get("remarks"),
                closedDate,
                (String) row.get("closed_by")
        );
    }

    private BigDecimal amount(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? ZERO : (BigDecimal) value;
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
