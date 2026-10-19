package com.cbms.service;

import com.cbms.dto.BankFixedDepositPrematureWithdrawalRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BankFixedDepositPrematureWithdrawalService {

    private static final BigDecimal ZERO =
            new BigDecimal("0.00");

    private static final BigDecimal DAYS_IN_YEAR =
            new BigDecimal("365");

    private final JdbcTemplate jdbc;

    public BankFixedDepositPrematureWithdrawalService(
            JdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> withdraw(
            Long fdId,
            BankFixedDepositPrematureWithdrawalRequest request,
            Authentication authentication
    ) {
        if (fdId == null || fdId <= 0) {
            throw badRequest("Invalid fixed deposit ID");
        }

        if (request == null
                || request.destinationAccountId() == null
                || request.destinationAccountId() <= 0) {
            throw badRequest("Destination account is required");
        }

        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required"
            );
        }

        String username = authentication.getName();
        LocalDate today = LocalDate.now();

        // ----------------------------------------------------
        // 1. Lock the FD.
        // ----------------------------------------------------

        List<Map<String, Object>> deposits = jdbc.queryForList(
                """
                SELECT *
                FROM public.bank_fixed_deposit
                WHERE fd_id = ?
                FOR UPDATE
                """,
                fdId
        );

        if (deposits.isEmpty()) {
            throw notFound("Fixed deposit not found");
        }

        Map<String, Object> fd = deposits.get(0);

        if (!"ACTIVE".equals(fd.get("fd_sts"))) {
            throw conflict(
                    "Only ACTIVE fixed deposits can be withdrawn"
            );
        }

        LocalDate placementDate =
                ((java.sql.Date) fd.get("placement_dt"))
                        .toLocalDate();

        LocalDate maturityDate =
                ((java.sql.Date) fd.get("maturity_dt"))
                        .toLocalDate();

        if (!today.isBefore(maturityDate)) {
            throw conflict(
                    "FD has reached maturity. Use maturity processing."
            );
        }

        if (today.isBefore(placementDate)) {
            throw conflict("FD placement date is in the future");
        }

        int holdingDays = Math.toIntExact(
                ChronoUnit.DAYS.between(placementDate, today)
        );

        Long customerId =
                ((Number) fd.get("cust_id")).longValue();

        Long schemeId =
                ((Number) fd.get("schm_id")).longValue();

        BigDecimal principal =
                money((BigDecimal) fd.get("principal_amt"));

        BigDecimal contractedRate =
                (BigDecimal) fd.get("interest_rate");

        if (!"MYR".equals(fd.get("ccy_cd"))) {
            throw conflict("Only MYR withdrawals are supported");
        }

        // ----------------------------------------------------
        // 2. Prevent duplicate withdrawal.
        // ----------------------------------------------------

        Long existing = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.bank_fd_premature_withdrawal
                WHERE fd_id = ?
                """,
                Long.class,
                fdId
        );

        if (existing != null && existing > 0) {
            throw conflict(
                    "Fixed deposit has already been withdrawn"
            );
        }

        // ----------------------------------------------------
        // 3. Verify customer.
        // ----------------------------------------------------

        List<String> statuses = jdbc.queryForList(
                """
                SELECT sts
                FROM public.customer_master
                WHERE cust_id = ?
                """,
                String.class,
                customerId
        );

        if (statuses.size() != 1
                || !"ACTIVE".equals(statuses.get(0))) {
            throw conflict("Customer must be ACTIVE");
        }

        // ----------------------------------------------------
        // 4. Verify original funding.
        // ----------------------------------------------------

        List<Map<String, Object>> funding = jdbc.queryForList(
                """
                SELECT p.source_acct_id
                FROM public.bank_fixed_deposit_placement p
                JOIN public.bank_transaction t
                  ON t.txn_ref = p.posting_ref
                WHERE p.fd_id = ?
                  AND p.placement_sts = 'POSTED'
                  AND p.placement_amt = ?
                  AND p.ccy_cd = 'MYR'
                  AND p.funding_typ IN (
                      'FD_PLACEMENT',
                      'FD_RENEWAL'
                  )
                  AND t.txn_typ = p.funding_typ
                  AND t.txn_sts = 'POSTED'
                  AND t.acct_id = p.source_acct_id
                  AND t.txn_amt = p.placement_amt
                  AND t.ccy_cd = 'MYR'
                  AND EXISTS (
                      SELECT 1
                      FROM public.bank_ledger_entry e
                      JOIN public.bank_ledger_account g
                        ON g.gl_id = e.gl_id
                      WHERE e.txn_id = t.txn_id
                        AND g.gl_cd = '200200'
                        AND e.entry_side = 'CREDIT'
                        AND e.entry_amt = p.placement_amt
                  )
                  AND (
                      p.funding_typ = 'FD_PLACEMENT'
                      OR EXISTS (
                          SELECT 1
                          FROM public.bank_fixed_deposit_renewal r
                          JOIN public.bank_fixed_deposit_maturity m
                            ON m.fd_id = r.original_fd_id
                          WHERE r.renewed_fd_id = p.fd_id
                            AND r.renewal_sts = 'PROCESSED'
                            AND r.renewal_typ = 'PRINCIPAL_ONLY'
                            AND r.capitalized_interest = 0
                            AND r.renewal_amt = p.placement_amt
                            AND m.maturity_sts = 'PROCESSED'
                            AND EXISTS (
                                SELECT 1
                                FROM public.bank_ledger_entry e
                                JOIN public.bank_ledger_account g
                                  ON g.gl_id = e.gl_id
                                WHERE e.txn_id = t.txn_id
                                  AND g.gl_cd = '200200'
                                  AND e.entry_side = 'DEBIT'
                                  AND e.entry_amt = p.placement_amt
                            )
                      )
                  )
                """,
                fdId,
                principal
        );

        if (funding.size() != 1) {
            throw conflict("FD funding is not verified");
        }

        // ----------------------------------------------------
        // 5. Select effective-dated policy.
        //
        // Policy is selected as of the FD placement date.
        // ----------------------------------------------------

        List<Map<String, Object>> policies =
                jdbc.queryForList(
                        """
                        SELECT *
                        FROM public.bank_fd_premature_policy
                        WHERE schm_id = ?
                          AND policy_sts = 'ACTIVE'
                          AND eff_dt <= ?
                        ORDER BY eff_dt DESC
                        LIMIT 1
                        """,
                        schemeId,
                        placementDate
                );

        if (policies.isEmpty()) {
            throw conflict(
                    "No premature withdrawal policy found"
            );
        }

        Map<String, Object> policy = policies.get(0);

        Long policyId =
                ((Number) policy.get("policy_id")).longValue();

        int minimumDays =
                ((Number) policy.get("min_holding_days"))
                        .intValue();

        int lockInDays =
                ((Number) policy.get("lock_in_days"))
                        .intValue();

        String minimumTreatment =
                (String) policy.get("min_holding_treatment");

        String interestMethod =
                (String) policy.get("interest_method");

        BigDecimal configuredPenalty =
                (BigDecimal) policy.get("penalty_rate");

        if (holdingDays < lockInDays) {
            throw conflict(
                    "FD is still within the lock-in period"
            );
        }

        if (holdingDays < minimumDays
                && "REJECT_WITHDRAWAL".equals(
                minimumTreatment)) {
            throw conflict(
                    "Minimum holding period has not been met"
            );
        }

        // ----------------------------------------------------
        // 6. Calculate applicable interest rate.
        // ----------------------------------------------------

        BigDecimal recalculatedRate = ZERO;
        BigDecimal appliedPenalty = ZERO;
        BigDecimal effectiveRate = ZERO;

        boolean zeroInterest =
                holdingDays < minimumDays
                        || "FORFEIT_ALL".equals(interestMethod);

        if (!zeroInterest) {

            if ("CONTRACT_RATE".equals(interestMethod)) {

                recalculatedRate = contractedRate;

            } else if ("HISTORICAL_RATE".equals(
                    interestMethod)) {

                // Use the longest historical tenor not exceeding
                // the actual holding period, with rates effective
                // on or before the original placement date.

                List<BigDecimal> historicalRates =
                        jdbc.queryForList(
                                """
                                SELECT interest_rate
                                FROM public.bank_fd_historical_rate
                                WHERE schm_id = ?
                                  AND rate_sts = 'ACTIVE'
                                  AND tenor_days <= ?
                                  AND eff_dt <= ?
                                ORDER BY tenor_days DESC, eff_dt DESC
                                LIMIT 1
                                """,
                                BigDecimal.class,
                                schemeId,
                                holdingDays,
                                placementDate
                        );

                if (historicalRates.isEmpty()) {
                    throw conflict(
                            "No eligible historical FD rate found"
                    );
                }

                recalculatedRate = historicalRates.get(0);

            } else {
                throw conflict(
                        "Unsupported interest calculation method"
                );
            }

            appliedPenalty = configuredPenalty;

            effectiveRate = recalculatedRate
                    .subtract(appliedPenalty)
                    .max(BigDecimal.ZERO);
        }

        // ----------------------------------------------------
        // 7. Calculate eligible interest using ACT/365.
        // ----------------------------------------------------

        BigDecimal eligibleInterest = principal
                .multiply(effectiveRate)
                .multiply(BigDecimal.valueOf(holdingDays))
                .divide(
                        new BigDecimal("36500"),
                        2,
                        RoundingMode.HALF_UP
                );

        // ----------------------------------------------------
        // 8. Verify posted accruals.
        //
        // This implementation assumes the normal daily accrual
        // schedule covers placementDate + 1 through yesterday.
        // ----------------------------------------------------

        Long missingDays = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM generate_series(
                    ?::date,
                    (?::date - 1),
                    INTERVAL '1 day'
                ) AS expected(accrual_dt)
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM public.bank_fixed_deposit_accrual a
                    WHERE a.fd_id = ?
                      AND a.accrual_dt =
                          expected.accrual_dt::date
                      AND a.accrual_sts = 'POSTED'
                )
                """,
                Long.class,
                placementDate,
                today,
                fdId
        );

        if (missingDays != null && missingDays > 0) {
            throw conflict(
                    "FD daily accruals are incomplete: "
                            + missingDays + " missing day(s)"
            );
        }

        // Reject future-dated or today's accruals to avoid
        // settling interest beyond the withdrawal cutoff.
        Long invalidAccruals = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.bank_fixed_deposit_accrual
                WHERE fd_id = ?
                  AND accrual_dt >= ?
                """,
                Long.class,
                fdId,
                today
        );

        if (invalidAccruals != null && invalidAccruals > 0) {
            throw conflict(
                    "FD has accruals on or after withdrawal date"
            );
        }

        // ----------------------------------------------------
        // 9. Reconcile accrued and previously paid interest.
        // ----------------------------------------------------

        BigDecimal accruedInterest = money(
                jdbc.queryForObject(
                        """
                        SELECT COALESCE(
                            SUM(accrued_interest), 0
                        )
                        FROM public.bank_fixed_deposit_accrual
                        WHERE fd_id = ?
                          AND accrual_sts = 'POSTED'
                        """,
                        BigDecimal.class,
                        fdId
                ));

        BigDecimal previouslyPaid = money(
                jdbc.queryForObject(
                        """
                        SELECT COALESCE(
                            SUM(payout_amt), 0
                        )
                        FROM public.bank_fixed_deposit_interest_payout
                        WHERE fd_id = ?
                          AND payout_sts = 'POSTED'
                        """,
                        BigDecimal.class,
                        fdId
                ));

        if (previouslyPaid.compareTo(accruedInterest) > 0) {
            throw conflict(
                    "Previously paid interest exceeds accrued interest"
            );
        }

        if (previouslyPaid.compareTo(eligibleInterest) > 0) {
            throw conflict(
                    "Previously paid interest exceeds eligible "
                            + "premature withdrawal interest. "
                            + "Interest recovery is not yet supported."
            );
        }

        BigDecimal unpaidAccrued = accruedInterest
                .subtract(previouslyPaid);

        BigDecimal interestToPay = eligibleInterest
                .subtract(previouslyPaid);

        BigDecimal interestAdjustment = eligibleInterest
                .subtract(accruedInterest);

        BigDecimal settlementAmount = principal
                .add(interestToPay);

        // ----------------------------------------------------
        // 10. Lock and validate destination account.
        // ----------------------------------------------------

        Long destinationId = request.destinationAccountId();

        List<Map<String, Object>> accounts =
                jdbc.queryForList(
                        """
                        SELECT *
                        FROM public.bank_account
                        WHERE acct_id = ?
                        FOR UPDATE
                        """,
                        destinationId
                );

        if (accounts.isEmpty()) {
            throw notFound("Destination account not found");
        }

        Map<String, Object> account = accounts.get(0);

        if (((Number) account.get("cust_id")).longValue()
                != customerId) {
            throw conflict(
                    "Destination account belongs to another customer"
            );
        }

        if (!"ACTIVE".equals(account.get("acct_sts"))) {
            throw conflict(
                    "Destination account must be ACTIVE"
            );
        }

        if (!"MYR".equals(account.get("ccy_cd"))) {
            throw conflict("Only MYR accounts are supported");
        }

        String accountType =
                (String) account.get("acct_typ");

        if (!"SAVINGS".equals(accountType)
                && !"CURRENT".equals(accountType)) {
            throw conflict(
                    "Destination must be SAVINGS or CURRENT"
            );
        }

        List<Map<String, Object>> balances =
                jdbc.queryForList(
                        """
                        SELECT *
                        FROM public.bank_account_balance
                        WHERE acct_id = ?
                        FOR UPDATE
                        """,
                        destinationId
                );

        if (balances.isEmpty()) {
            throw conflict(
                    "Destination account balance not found"
            );
        }

        // ----------------------------------------------------
        // 11. Find required GL accounts.
        // ----------------------------------------------------

        Long fdGl = findGl("200200");
        Long accruedGl = findGl("200300");
        Long expenseGl = findGl("500100");
        Long depositGl = findGl("200100");

        // ----------------------------------------------------
        // 12. Create posted transaction.
        // ----------------------------------------------------

        String withdrawalRef =
                "FD-PW-" + UUID.randomUUID();

        String transactionRef =
                "TXN-" + UUID.randomUUID();

        Long transactionId = jdbc.queryForObject(
                """
                INSERT INTO public.bank_transaction
                (
                    txn_ref,
                    acct_id,
                    txn_typ,
                    txn_amt,
                    ccy_cd,
                    txn_sts,
                    idempotency_key,
                    crt_by
                )
                VALUES (
                    ?,
                    ?,
                    'FD_PREMATURE_WITHDRAWAL',
                    ?,
                    'MYR',
                    'POSTED',
                    ?,
                    ?
                )
                RETURNING txn_id
                """,
                Long.class,
                transactionRef,
                destinationId,
                settlementAmount,
                "FD-PREMATURE:" + fdId,
                username
        );

        // ----------------------------------------------------
        // 13. Post balanced GL entries.
        // ----------------------------------------------------

        // Release the FD principal liability.
        postEntry(
                transactionId,
                fdGl,
                null,
                "DEBIT",
                principal
        );

        // Release unpaid accrued interest liability.
        postEntry(
                transactionId,
                accruedGl,
                null,
                "DEBIT",
                unpaidAccrued
        );

        // Adjust previously recognized interest expense.
        if (interestAdjustment.signum() < 0) {

            postEntry(
                    transactionId,
                    expenseGl,
                    null,
                    "CREDIT",
                    interestAdjustment.abs()
            );

        } else if (interestAdjustment.signum() > 0) {

            postEntry(
                    transactionId,
                    expenseGl,
                    null,
                    "DEBIT",
                    interestAdjustment
            );
        }

        // Credit the destination customer's deposit liability.
        postEntry(
                transactionId,
                depositGl,
                destinationId,
                "CREDIT",
                settlementAmount
        );

        // ----------------------------------------------------
        // 14. Verify debit and credit totals.
        // ----------------------------------------------------

        Map<String, Object> totals = jdbc.queryForMap(
                """
                SELECT
                    COALESCE(
                        SUM(entry_amt) FILTER (
                            WHERE entry_side = 'DEBIT'
                        ), 0
                    ) AS debit_total,
                    COALESCE(
                        SUM(entry_amt) FILTER (
                            WHERE entry_side = 'CREDIT'
                        ), 0
                    ) AS credit_total
                FROM public.bank_ledger_entry
                WHERE txn_id = ?
                """,
                transactionId
        );

        BigDecimal debitTotal =
                (BigDecimal) totals.get("debit_total");

        BigDecimal creditTotal =
                (BigDecimal) totals.get("credit_total");

        if (debitTotal.compareTo(creditTotal) != 0) {
            throw conflict(
                    "Premature withdrawal GL entries are unbalanced"
            );
        }

        // ----------------------------------------------------
        // 15. Credit destination account.
        // ----------------------------------------------------

        jdbc.update(
                """
                UPDATE public.bank_account_balance
                SET ledger_bal = ledger_bal + ?,
                    avail_bal = avail_bal + ?,
                    upd_dt = CURRENT_TIMESTAMP,
                    upd_by = ?
                WHERE acct_id = ?
                """,
                settlementAmount,
                settlementAmount,
                username,
                destinationId
        );

        // ----------------------------------------------------
        // 16. Insert settlement record.
        // ----------------------------------------------------

        Long withdrawalId = jdbc.queryForObject(
                """
                INSERT INTO public.bank_fd_premature_withdrawal
                (
                    withdrawal_ref,
                    fd_id,
                    policy_id,
                    dest_acct_id,
                    withdrawal_dt,
                    holding_days,
                    principal_amt,
                    contracted_rate,
                    recalculated_rate,
                    penalty_rate,
                    effective_rate,
                    eligible_interest,
                    accrued_interest,
                    previously_paid_interest,
                    interest_adjustment,
                    interest_to_pay,
                    interest_recovery,
                    settlement_amt,
                    txn_id,
                    posting_ref,
                    withdrawal_sts,
                    remarks,
                    crt_by
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, 0, ?, ?, ?,
                    'POSTED', ?, ?
                )
                RETURNING withdrawal_id
                """,
                Long.class,
                withdrawalRef,
                fdId,
                policyId,
                destinationId,
                today,
                holdingDays,
                principal,
                contractedRate,
                recalculatedRate,
                appliedPenalty,
                effectiveRate,
                eligibleInterest,
                accruedInterest,
                previouslyPaid,
                interestAdjustment,
                interestToPay,
                settlementAmount,
                transactionId,
                transactionRef,
                request.remarks(),
                username
        );

        // ----------------------------------------------------
        // 17. Close FD.
        // ----------------------------------------------------

        int updated = jdbc.update(
                """
                UPDATE public.bank_fixed_deposit
                SET fd_sts = 'CLOSED',
                    upd_dt = CURRENT_TIMESTAMP,
                    upd_by = ?
                WHERE fd_id = ?
                  AND fd_sts = 'ACTIVE'
                """,
                username,
                fdId
        );

        if (updated != 1) {
            throw conflict(
                    "Fixed deposit closure failed"
            );
        }

        // ----------------------------------------------------
        // 18. Return settlement response.
        // ----------------------------------------------------

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("withdrawalId", withdrawalId);
        result.put("withdrawalReference", withdrawalRef);
        result.put("fdId", fdId);
        result.put("destinationAccountId", destinationId);
        result.put("withdrawalDate", today);
        result.put("holdingDays", holdingDays);
        result.put("principalAmount", principal);
        result.put("contractedRate", contractedRate);
        result.put("recalculatedRate", recalculatedRate);
        result.put("penaltyRate", appliedPenalty);
        result.put("effectiveRate", effectiveRate);
        result.put("eligibleInterest", eligibleInterest);
        result.put("accruedInterest", accruedInterest);
        result.put("previouslyPaidInterest", previouslyPaid);
        result.put("interestAdjustment", interestAdjustment);
        result.put("interestToPay", interestToPay);
        result.put("settlementAmount", settlementAmount);
        result.put("transactionId", transactionId);
        result.put("transactionReference", transactionRef);
        result.put("totalDebit", debitTotal);
        result.put("totalCredit", creditTotal);
        result.put("status", "CLOSED");

        return result;
    }

    private void postEntry(
            Long transactionId,
            Long glId,
            Long accountId,
            String side,
            BigDecimal amount
    ) {
        if (amount.signum() <= 0) {
            return;
        }

        jdbc.update(
                """
                INSERT INTO public.bank_ledger_entry
                (
                    txn_id,
                    gl_id,
                    acct_id,
                    entry_side,
                    entry_amt
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                transactionId,
                glId,
                accountId,
                side,
                amount
        );
    }

    private Long findGl(String code) {

        List<Long> ids = jdbc.queryForList(
                """
                SELECT gl_id
                FROM public.bank_ledger_account
                WHERE gl_cd = ?
                  AND ccy_cd = 'MYR'
                  AND act_flg = TRUE
                """,
                Long.class,
                code
        );

        if (ids.size() != 1) {
            throw conflict(
                    "Required GL is unavailable: " + code
            );
        }

        return ids.get(0);
    }

    private BigDecimal money(BigDecimal value) {

        if (value == null) {
            return ZERO;
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private ResponseStatusException badRequest(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private ResponseStatusException notFound(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }

    private ResponseStatusException conflict(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}