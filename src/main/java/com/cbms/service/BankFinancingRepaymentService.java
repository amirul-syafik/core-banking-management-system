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
import java.util.*;

@Service
public class BankFinancingRepaymentService {

    private static final BigDecimal ZERO =
            new BigDecimal("0.00");

    private final JdbcTemplate jdbc;

    public BankFinancingRepaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public BankFinancingRepaymentResponse repay(
            Long facId,
            BankFinancingRepaymentRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();

        BigDecimal amount = request.amount();

        if (amount == null || amount.signum() <= 0
                || amount.scale() > 2
                || amount.precision() - amount.scale() > 17) {
            throw conflict("Invalid repayment amount");
        }

        amount = amount.setScale(2, RoundingMode.UNNECESSARY);

        if (!Set.of(
                "BANK_TRANSFER", "CASH",
                "INTERNAL_TRANSFER", "OTHER"
        ).contains(request.paymentChannel())) {
            throw conflict("Unsupported payment channel");
        }

        // Serialize repayments for the same facility.
        List<Map<String, Object>> facilities = jdbc.queryForList(
                """
                SELECT fac_id, fac_sts, fac_typ, ccy_cd
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

        // Check existing repayment before checking facility status.
        // This allows a safe retry even if the facility status changes.
        List<Map<String, Object>> existing = jdbc.queryForList(
                """
                SELECT *
                FROM public.bank_financing_repayment
                WHERE idempotency_key = ?
                """,
                request.idempotencyKey()
        );

        if (!existing.isEmpty()) {
            Map<String, Object> previous = existing.get(0);

            boolean matches =
                    ((Number) previous.get("fac_id"))
                            .longValue() == facId
                            && ((BigDecimal) previous.get("repay_amt"))
                            .compareTo(amount) == 0
                            && Objects.equals(
                            previous.get("payment_ref"),
                            request.paymentRef()
                    )
                            && Objects.equals(
                            previous.get("payment_channel"),
                            request.paymentChannel()
                    );

            if (!matches) {
                throw conflict(
                        "Idempotency key belongs to a different repayment"
                );
            }

            return loadResponse(
                    ((Number) previous.get("repay_id")).longValue()
            );
        }

        Map<String, Object> facility = facilities.get(0);

        if (!"ACTIVE".equals(facility.get("fac_sts"))) {
            throw conflict("Financing facility must be ACTIVE");
        }

        if (!"HIRE_PURCHASE".equals(facility.get("fac_typ"))
                || !"MYR".equals(facility.get("ccy_cd"))) {
            throw conflict("Unsupported financing facility");
        }

        Integer disbursementCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.bank_financing_disbursement
                WHERE fac_id = ?
                  AND disb_sts = 'POSTED'
                """,
                Integer.class,
                facId
        );

        if (disbursementCount == null || disbursementCount != 1) {
            throw conflict(
                    "Facility must have a posted disbursement"
            );
        }

        List<Map<String, Object>> schedules = jdbc.queryForList(
                """
                SELECT sched_id, inst_no,
                       principal_amt, interest_amt,
                       paid_principal, paid_interest
                FROM public.bank_financing_repayment_schedule
                WHERE fac_id = ?
                ORDER BY inst_no
                FOR UPDATE
                """,
                facId
        );

        if (schedules.isEmpty()) {
            throw conflict("Repayment schedule not found");
        }

        BigDecimal remainingContractual = ZERO;

        for (Map<String, Object> schedule : schedules) {
            remainingContractual = remainingContractual
                    .add(bd(schedule, "principal_amt"))
                    .subtract(bd(schedule, "paid_principal"))
                    .add(bd(schedule, "interest_amt"))
                    .subtract(bd(schedule, "paid_interest"));
        }

        if (amount.compareTo(remainingContractual) > 0) {
            throw conflict(
                    "Repayment exceeds remaining scheduled amount: "
                            + remainingContractual
            );
        }

        if (remainingContractual.signum() <= 0) {
            throw conflict("Financing schedule is fully paid");
        }

        String reference = "REPAY-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT);

        Long repayId = jdbc.queryForObject(
                """
                INSERT INTO public.bank_financing_repayment
                (
                    fac_id, repay_ref, idempotency_key,
                    repay_amt, payment_ref, payment_channel,
                    repay_sts, remarks, crt_by
                )
                VALUES (?, ?, ?, ?, ?, ?, 'POSTED', ?, ?)
                RETURNING repay_id
                """,
                Long.class,
                facId,
                reference,
                request.idempotencyKey(),
                amount,
                request.paymentRef(),
                request.paymentChannel(),
                request.remarks(),
                username
        );

        BigDecimal unallocated = amount;
        BigDecimal totalPrincipal = ZERO;
        BigDecimal totalInterest = ZERO;

        for (Map<String, Object> schedule : schedules) {

            if (unallocated.signum() == 0) {
                break;
            }

            Long schedId =
                    ((Number) schedule.get("sched_id")).longValue();

            BigDecimal principalDue =
                    bd(schedule, "principal_amt")
                            .subtract(bd(schedule, "paid_principal"));

            BigDecimal interestDue =
                    bd(schedule, "interest_amt")
                            .subtract(bd(schedule, "paid_interest"));

            // Interest first, then principal.
            BigDecimal interestPaid = unallocated.min(interestDue);
            unallocated = unallocated.subtract(interestPaid);

            BigDecimal principalPaid = unallocated.min(principalDue);
            unallocated = unallocated.subtract(principalPaid);

            if (interestPaid.signum() == 0
                    && principalPaid.signum() == 0) {
                continue;
            }

            jdbc.update(
                    """
                    INSERT INTO public.bank_financing_repayment_allocation
                    (
                        repay_id, sched_id,
                        principal_amt, interest_amt
                    )
                    VALUES (?, ?, ?, ?)
                    """,
                    repayId,
                    schedId,
                    principalPaid,
                    interestPaid
            );

            jdbc.update(
                    """
                    UPDATE public.bank_financing_repayment_schedule
                    SET paid_principal = paid_principal + ?,
                        paid_interest = paid_interest + ?,
                        inst_sts = CASE
                            WHEN paid_principal + ? = principal_amt
                             AND paid_interest + ? = interest_amt
                                THEN 'PAID'
                            ELSE 'PARTIAL'
                        END
                    WHERE sched_id = ?
                    """,
                    principalPaid,
                    interestPaid,
                    principalPaid,
                    interestPaid,
                    schedId
            );

            totalPrincipal = totalPrincipal.add(principalPaid);
            totalInterest = totalInterest.add(interestPaid);
        }

        if (unallocated.signum() != 0
                || totalPrincipal.add(totalInterest)
                .compareTo(amount) != 0) {
            throw conflict("Repayment allocation does not reconcile");
        }

        // Debit confirmed repayment clearing.
        insertJournal(
                repayId, 1, "REPAYMENT_CLEARING",
                amount, ZERO, username
        );

        // Credit principal receivable.
        if (totalPrincipal.signum() > 0) {
            insertJournal(
                    repayId, 2, "HP_PRINCIPAL_RECEIVABLE",
                    ZERO, totalPrincipal, username
            );
        }

        // Credit interest income.
        if (totalInterest.signum() > 0) {
            insertJournal(
                    repayId, 3, "HP_INTEREST_INCOME",
                    ZERO, totalInterest, username
            );
        }

        Map<String, Object> totals = jdbc.queryForMap(
                """
                SELECT COALESCE(SUM(dr_amt), 0) AS total_dr,
                       COALESCE(SUM(cr_amt), 0) AS total_cr
                FROM public.bank_financing_repayment_journal
                WHERE repay_id = ?
                """,
                repayId
        );

        if (bd(totals, "total_dr")
                .compareTo(bd(totals, "total_cr")) != 0
                || bd(totals, "total_dr").compareTo(amount) != 0) {
            throw conflict("Repayment journal is not balanced");
        }

        return loadResponse(repayId);
    }

    @Transactional(readOnly = true)
    public List<BankFinancingRepaymentResponse> list(Long facId) {

        if (jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.bank_financing_facility
                WHERE fac_id = ?
                """,
                Integer.class,
                facId
        ) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Financing facility not found"
            );
        }

        List<Long> ids = jdbc.queryForList(
                """
                SELECT repay_id
                FROM public.bank_financing_repayment
                WHERE fac_id = ?
                ORDER BY repay_id DESC
                """,
                Long.class,
                facId
        );

        return ids.stream().map(this::loadResponse).toList();
    }

    private BankFinancingRepaymentResponse loadResponse(Long repayId) {

        Map<String, Object> repayment = jdbc.queryForMap(
                """
                SELECT *
                FROM public.bank_financing_repayment
                WHERE repay_id = ?
                """,
                repayId
        );

        List<BankFinancingRepaymentAllocationResponse> allocations =
                jdbc.query(
                        """
                        SELECT a.sched_id, s.inst_no,
                               a.principal_amt, a.interest_amt
                        FROM public.bank_financing_repayment_allocation a
                        JOIN public.bank_financing_repayment_schedule s
                          ON s.sched_id = a.sched_id
                        WHERE a.repay_id = ?
                        ORDER BY s.inst_no
                        """,
                        (rs, rowNum) ->
                                new BankFinancingRepaymentAllocationResponse(
                                        rs.getLong("sched_id"),
                                        rs.getInt("inst_no"),
                                        rs.getBigDecimal("principal_amt"),
                                        rs.getBigDecimal("interest_amt")
                                ),
                        repayId
                );

        return new BankFinancingRepaymentResponse(
                ((Number) repayment.get("repay_id")).longValue(),
                ((Number) repayment.get("fac_id")).longValue(),
                (String) repayment.get("repay_ref"),
                bd(repayment, "repay_amt"),
                (String) repayment.get("repay_sts"),
                (String) repayment.get("payment_ref"),
                (String) repayment.get("payment_channel"),
                allocations
        );
    }

    private void insertJournal(
            Long repayId,
            int lineNo,
            String glCode,
            BigDecimal debit,
            BigDecimal credit,
            String username
    ) {
        jdbc.update(
                """
                INSERT INTO public.bank_financing_repayment_journal
                (
                    repay_id, line_no, gl_cd,
                    dr_amt, cr_amt, crt_by
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                repayId, lineNo, glCode,
                debit, credit, username
        );
    }

    private BigDecimal bd(Map<String, Object> row, String column) {
        return (BigDecimal) row.get(column);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
