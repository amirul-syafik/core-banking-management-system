package com.cbms;

import com.cbms.dto.BankFixedDepositPrematureWithdrawalRequest;
import com.cbms.dto.BankFixedDepositRenewalRequest;
import com.cbms.service.BankFixedDepositMaturityProcessingService;
import com.cbms.service.BankFixedDepositPrematureWithdrawalService;
import com.cbms.service.BankFixedDepositRenewalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round 3C: PostgreSQL-backed FD premature uplift and accounting checks.
 * Only runs with the guarded corebank_lite_test financial fixture.
 * All writes (including the fixture) are rolled back per test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "/sql/financial-test-fixture.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BankFixedDepositPrematureSettlementIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired BankFixedDepositMaturityProcessingService maturityService;
    @Autowired BankFixedDepositRenewalService renewalService;
    @Autowired BankFixedDepositPrematureWithdrawalService upliftService;
    @Autowired com.cbms.service.BankFixedDepositInterestPayoutService payoutService;

    private Long accountId;
    private Long productId;
    private Long schemeId;
    private static final String ACTOR = "FD_LIFECYCLE_TEST";
    private static final BigDecimal PRINCIPAL = new BigDecimal("40.00");
    private final Authentication actor = new UsernamePasswordAuthenticationToken(
            ACTOR, "not-used", List.of());

    @BeforeEach
    void setup() {
        accountId = jdbc.queryForObject(
                "SELECT acct_id FROM public.bank_account WHERE acct_no = 'TEST-REV-900001'",
                Long.class);
        String id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        productId = jdbc.queryForObject("""
                INSERT INTO public.bank_product
                  (prod_cd, prod_nm, prod_cat, banking_typ, prod_sts, crt_by)
                VALUES (?, 'FD lifecycle test product', 'FIXED_DEPOSIT',
                        'CONVENTIONAL', 'ACTIVE', ?)
                RETURNING prod_id
                """, Long.class, "FLC" + id, ACTOR);
        schemeId = jdbc.queryForObject("""
                INSERT INTO public.bank_scheme
                  (prod_id, schm_cd, schm_nm, min_amt, max_amt,
                   min_tenor, max_tenor, schm_sts, crt_by)
                VALUES (?, ?, 'FD lifecycle test scheme', 1, 100000,
                        1, 12, 'ACTIVE', ?)
                RETURNING schm_id
                """, Long.class, productId, "FLS" + id, ACTOR);
        jdbc.update("""
                INSERT INTO public.bank_scheme_rate
                  (schm_id, rate_typ, fixed_rate_pct, eff_dt, rate_sts, crt_by)
                VALUES (?, 'FIXED', 3.65000, ?, 'ACTIVE', ?)
                """, schemeId, LocalDate.now().minusMonths(2), ACTOR);
        jdbc.update("""
                INSERT INTO public.bank_ledger_account
                  (gl_cd, gl_nm, gl_typ, ccy_cd, act_flg)
                VALUES ('200200', 'FD liability', 'LIABILITY', 'MYR', TRUE)
                ON CONFLICT (gl_cd) DO NOTHING
                """);
    }

    private int count(String query, Object... args) {
        return jdbc.queryForObject(query, Integer.class, args);
    }

    private BigDecimal balance() {
        return jdbc.queryForObject(
                "SELECT ledger_bal FROM public.bank_account_balance WHERE acct_id = ?",
                BigDecimal.class, accountId);
    }

    /**
     * The explicit one-day contractual period makes the maturity test deterministic.
     * This is a deliberately crafted integration fixture, not a production tenor rule.
     * Funding and ledger postings match the real FD_PLACEMENT accounting contract.
     */
    private Long fundedFd(LocalDate placement, LocalDate maturity, boolean includeAccrual) {
        String key = UUID.randomUUID().toString();
        Long fdId = jdbc.queryForObject("""
                INSERT INTO public.bank_fixed_deposit
                  (fd_ref, cust_id, prod_id, schm_id, fd_typ, principal_amt,
                   tenor_mth, placement_dt, maturity_dt, interest_rate,
                   fd_sts, crt_by)
                VALUES (?, -900001, ?, ?, 'FIXED_DEPOSIT', ?,
                        1, ?, ?, 3.650000, 'ACTIVE', ?)
                RETURNING fd_id
                """, Long.class, "FD-LC-" + key, productId, schemeId,
                PRINCIPAL, placement, maturity, ACTOR);

        String txRef = "TXN-FD-LC-" + key;
        Long txnId = jdbc.queryForObject("""
                INSERT INTO public.bank_transaction
                  (txn_ref, acct_id, txn_typ, txn_amt, ccy_cd, txn_sts,
                   idempotency_key, crt_by)
                VALUES (?, ?, 'FD_PLACEMENT', ?, 'MYR', 'POSTED', ?, ?)
                RETURNING txn_id
                """, Long.class, txRef, accountId, PRINCIPAL, "FD-LC:" + key, ACTOR);
        jdbc.update("""
                INSERT INTO public.bank_ledger_entry
                  (txn_id, gl_id, acct_id, entry_side, entry_amt)
                SELECT ?, gl_id, ?, 'DEBIT', ?
                FROM public.bank_ledger_account WHERE gl_cd = '200100'
                """, txnId, accountId, PRINCIPAL);
        jdbc.update("""
                INSERT INTO public.bank_ledger_entry
                  (txn_id, gl_id, acct_id, entry_side, entry_amt)
                SELECT ?, gl_id, NULL, 'CREDIT', ?
                FROM public.bank_ledger_account WHERE gl_cd = '200200'
                """, txnId, PRINCIPAL);
        jdbc.update("""
                UPDATE public.bank_account_balance
                SET ledger_bal = ledger_bal - ?, avail_bal = avail_bal - ?
                WHERE acct_id = ?
                """, PRINCIPAL, PRINCIPAL, accountId);
        jdbc.update("""
                INSERT INTO public.bank_fixed_deposit_placement
                  (placement_ref, fd_id, source_acct_id, placement_amt,
                   placement_dt, posting_ref, placement_sts, funding_typ, crt_by)
                VALUES (?, ?, ?, ?, ?, ?, 'POSTED', 'FD_PLACEMENT', ?)
                """, "FD-P-LC-" + key, fdId, accountId, PRINCIPAL,
                placement, txRef, ACTOR);

        if (includeAccrual) {
            for (LocalDate day = placement; day.isBefore(maturity); day = day.plusDays(1)) {
                jdbc.update("""
                        INSERT INTO public.bank_fixed_deposit_accrual
                          (fd_id, accrual_dt, daily_interest, accrued_interest,
                           cumulative_interest, posting_ref, accrual_sts, crt_by)
                        VALUES (?, ?, 0.004000000000, 0.00, 0.00, ?, 'POSTED', ?)
                        """, fdId, day, "FD-A-LC-" + UUID.randomUUID(), ACTOR);
            }
        }
        return fdId;
    }

    private Long matureOneDayFd() {
        Long fdId = fundedFd(LocalDate.now().minusDays(1), LocalDate.now(), true);
        assertEquals("MATURED", maturityService.process(fdId, ACTOR).get("status"));
        return fdId;
    }


    private Long prematureFd(int daysHeld) {
        LocalDate placement = LocalDate.now().minusDays(daysHeld);
        Long fdId = fundedFd(placement, LocalDate.now().plusMonths(1), false);
        // The uplift SQL checks placement date through yesterday, inclusively.
        for (LocalDate date = placement; date.isBefore(LocalDate.now()); date = date.plusDays(1)) {
            jdbc.update("""
                    INSERT INTO public.bank_fixed_deposit_accrual
                      (fd_id, accrual_dt, daily_interest, accrued_interest,
                       cumulative_interest, posting_ref, accrual_sts, crt_by)
                    VALUES (?, ?, 0.000000000000, 0.00, 0.00, ?, 'POSTED', ?)
                    """, fdId, date, "FD-PW-A-" + UUID.randomUUID(), ACTOR);
        }
        return fdId;
    }

    private void policy(int minimumDays, int lockInDays, String minimumTreatment,
                        String interestMethod, String penaltyRate) {
        jdbc.update("""
                INSERT INTO public.bank_fd_premature_policy
                  (schm_id, min_holding_days, lock_in_days,
                   min_holding_treatment, interest_method, penalty_rate,
                   eff_dt, policy_sts, crt_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """, schemeId, minimumDays, lockInDays, minimumTreatment,
                interestMethod, new BigDecimal(penaltyRate),
                LocalDate.now().minusMonths(3), ACTOR);
        for (String[] gl : new String[][] {
                {"200300", "FD Accrued Interest", "LIABILITY"},
                {"500100", "FD Interest Expense", "EXPENSE"}
        }) {
            jdbc.update("""
                    INSERT INTO public.bank_ledger_account
                      (gl_cd, gl_nm, gl_typ, ccy_cd, act_flg)
                    VALUES (?, ?, ?, 'MYR', TRUE)
                    ON CONFLICT (gl_cd) DO NOTHING
                    """, gl[0], gl[1], gl[2]);
        }
    }

    private Map<String, Object> uplift(Long fdId) {
        return upliftService.withdraw(fdId,
                new BankFixedDepositPrematureWithdrawalRequest(accountId, "FD 3C integration test"), actor);
    }

    private BigDecimal money(Object value) {
        return ((BigDecimal) value).setScale(2);
    }

    private void verifyPostedSettlement(Long fdId, Map<String, Object> result,
                                        BigDecimal expectedInterest) {
        assertEquals("CLOSED", result.get("status"));
        assertEquals("CLOSED", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?", String.class, fdId));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fd_premature_withdrawal WHERE fd_id = ?", fdId));
        Long txnId = ((Number) result.get("transactionId")).longValue();
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_transaction
                WHERE txn_id = ? AND txn_typ = 'FD_PREMATURE_WITHDRAWAL'
                  AND txn_sts = 'POSTED' AND idempotency_key = ?
                """, txnId, "FD-PREMATURE:" + fdId));
        assertEquals(0, money(result.get("interestToPay")).compareTo(expectedInterest));
        assertEquals(0, money(result.get("settlementAmount"))
                .compareTo(PRINCIPAL.add(expectedInterest)));
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT COALESCE(SUM(entry_amt) FILTER (WHERE entry_side = 'DEBIT'), 0) debit,
                       COALESCE(SUM(entry_amt) FILTER (WHERE entry_side = 'CREDIT'), 0) credit
                FROM public.bank_ledger_entry WHERE txn_id = ?
                """, txnId);
        assertEquals(0, ((BigDecimal) totals.get("debit"))
                .compareTo((BigDecimal) totals.get("credit")), "GL posting must balance");
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id
                WHERE e.txn_id = ? AND g.gl_cd = '200200'
                  AND e.entry_side = 'DEBIT' AND e.entry_amt = ?
                """, txnId, PRINCIPAL));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id
                WHERE e.txn_id = ? AND g.gl_cd = '200100'
                  AND e.entry_side = 'CREDIT' AND e.acct_id = ? AND e.entry_amt = ?
                """, txnId, accountId, PRINCIPAL.add(expectedInterest)));
    }

    @Test
    void contractRateUpliftCreditsPrincipalAndEligibleInterestExactlyOnce() {
        policy(0, 0, "ZERO_INTEREST", "CONTRACT_RATE", "0");
        Long fdId = prematureFd(5);
        BigDecimal before = balance();
        Map<String, Object> result = uplift(fdId);
        // RM40 * 3.65% * 5/365 = RM0.02 (HALF_UP).
        BigDecimal interest = new BigDecimal("0.02");
        verifyPostedSettlement(fdId, result, interest);
        assertEquals(0, balance().compareTo(before.add(PRINCIPAL).add(interest)));
    }

    @Test
    void minimumHoldingZeroInterestPaysOnlyPrincipal() {
        policy(7, 0, "ZERO_INTEREST", "CONTRACT_RATE", "0");
        Long fdId = prematureFd(2);
        BigDecimal before = balance();
        Map<String, Object> result = uplift(fdId);
        verifyPostedSettlement(fdId, result, new BigDecimal("0.00"));
        assertEquals(0, balance().compareTo(before.add(PRINCIPAL)));
        assertEquals(0, money(result.get("effectiveRate")).compareTo(new BigDecimal("0.00")));
    }

    @Test
    void historicalRateMinusPenaltyDeterminesEligibleInterest() {
        policy(0, 0, "ZERO_INTEREST", "HISTORICAL_RATE", "0.500000");
        jdbc.update("""
                INSERT INTO public.bank_fd_historical_rate
                  (schm_id, tenor_days, interest_rate, eff_dt, rate_sts, crt_by)
                VALUES (?, 7, 1.500000, ?, 'ACTIVE', ?)
                """, schemeId, LocalDate.now().minusMonths(3), ACTOR);
        Long fdId = prematureFd(10);
        Map<String, Object> result = uplift(fdId);
        // RM40 * (1.50 - 0.50)% * 10 / 365 = RM0.01.
        verifyPostedSettlement(fdId, result, new BigDecimal("0.01"));
        assertEquals(0, ((BigDecimal) result.get("effectiveRate"))
                .compareTo(new BigDecimal("1.000000")));
    }

    @Test
    void duplicateUpliftCannotPostAnotherSettlement() {
        policy(0, 0, "ZERO_INTEREST", "CONTRACT_RATE", "0");
        Long fdId = prematureFd(5);
        uplift(fdId);
        BigDecimal after = balance();
        int txnCount = count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?",
                "FD-PREMATURE:" + fdId);
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> uplift(fdId));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertEquals(0, after.compareTo(balance()));
        assertEquals(txnCount, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?",
                "FD-PREMATURE:" + fdId));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fd_premature_withdrawal WHERE fd_id = ?", fdId));
    }

    @Test
    void missingDailyAccrualRejectsUpliftWithoutPosting() {
        policy(0, 0, "ZERO_INTEREST", "CONTRACT_RATE", "0");
        Long fdId = prematureFd(5);
        jdbc.update("DELETE FROM public.bank_fixed_deposit_accrual WHERE fd_id = ? AND accrual_dt = ?",
                fdId, LocalDate.now().minusDays(2));
        BigDecimal before = balance();
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> uplift(fdId));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertEquals(0, before.compareTo(balance()));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?", String.class, fdId));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_fd_premature_withdrawal WHERE fd_id = ?", fdId));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?",
                "FD-PREMATURE:" + fdId));
    }

    @Test
    void lockInPeriodRejectsUpliftWithoutPosting() {
        policy(0, 10, "ZERO_INTEREST", "CONTRACT_RATE", "0");
        Long fdId = prematureFd(5);
        BigDecimal before = balance();
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> uplift(fdId));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertEquals(0, before.compareTo(balance()));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?", String.class, fdId));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_fd_premature_withdrawal WHERE fd_id = ?", fdId));
    }

    /** Post a backed accrual and an actual interest payout before premature uplift. */
    private void payPostedInterest(Long fdId, BigDecimal paid) {
        String ref = "TXN-FD-REC-" + UUID.randomUUID();
        Long txnId = jdbc.queryForObject("""
                INSERT INTO public.bank_transaction
                 (txn_ref, acct_id, txn_typ, txn_amt, ccy_cd, txn_sts, idempotency_key, crt_by)
                VALUES (?, ?, 'FD_INTEREST_ACCRUAL', ?, 'MYR', 'POSTED', ?, ?)
                RETURNING txn_id
                """, Long.class, ref, accountId, paid, "FD-REC:" + ref, ACTOR);
        jdbc.update("""
                INSERT INTO public.bank_ledger_entry(txn_id, gl_id, acct_id, entry_side, entry_amt)
                SELECT ?, gl_id, NULL, 'DEBIT', ? FROM public.bank_ledger_account WHERE gl_cd = '500100'
                """, txnId, paid);
        jdbc.update("""
                INSERT INTO public.bank_ledger_entry(txn_id, gl_id, acct_id, entry_side, entry_amt)
                SELECT ?, gl_id, NULL, 'CREDIT', ? FROM public.bank_ledger_account WHERE gl_cd = '200300'
                """, txnId, paid);
        int updated = jdbc.update("""
                UPDATE public.bank_fixed_deposit_accrual
                SET accrued_interest = ?, cumulative_interest = ?, posting_ref = ?
                WHERE accrual_id = (SELECT MIN(accrual_id) FROM public.bank_fixed_deposit_accrual WHERE fd_id = ?)
                """, paid, paid, ref, fdId);
        assertEquals(1, updated);
        payoutService.payout(fdId,
                new com.cbms.dto.BankFixedDepositInterestPayoutRequest(accountId), actor);
    }

    @Test
    void alreadyPaidInterestAboveEligibleIsRecoveredFromPrincipalAndJournalBalances() {
        policy(0, 0, "ZERO_INTEREST", "CONTRACT_RATE", "0");
        Long fdId = prematureFd(5);
        payPostedInterest(fdId, new BigDecimal("1.00"));
        BigDecimal before = balance();
        Map<String, Object> result = uplift(fdId);
        assertEquals("CLOSED", result.get("status"));
        assertEquals(0, money(result.get("eligibleInterest")).compareTo(new BigDecimal("0.02")));
        assertEquals(0, money(result.get("interestRecovery")).compareTo(new BigDecimal("0.98")));
        assertEquals(0, money(result.get("interestToPay")).compareTo(new BigDecimal("0.00")));
        assertEquals(0, money(result.get("settlementAmount")).compareTo(new BigDecimal("39.02")));
        assertEquals(0, balance().compareTo(before.add(new BigDecimal("39.02"))));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_fd_premature_withdrawal
                WHERE fd_id = ? AND interest_recovery = 0.98 AND settlement_amt = 39.02
                """, fdId));
        Long txnId = ((Number) result.get("transactionId")).longValue();
        Map<String,Object> totals = jdbc.queryForMap("""
                SELECT COALESCE(SUM(entry_amt) FILTER (WHERE entry_side='DEBIT'),0) dr,
                       COALESCE(SUM(entry_amt) FILTER (WHERE entry_side='CREDIT'),0) cr
                FROM public.bank_ledger_entry WHERE txn_id=?
                """, txnId);
        assertEquals(0, ((BigDecimal)totals.get("dr")).compareTo((BigDecimal)totals.get("cr")));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_ledger_account g ON g.gl_id=e.gl_id
                WHERE e.txn_id=? AND g.gl_cd='500100' AND e.entry_side='CREDIT' AND e.entry_amt=0.98
                """, txnId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_ledger_account g ON g.gl_id=e.gl_id
                WHERE e.txn_id=? AND g.gl_cd='200100' AND e.entry_side='CREDIT' AND e.entry_amt=39.02
                """, txnId));
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class, () -> uplift(fdId)).getStatusCode());
    }

    @Test
    void interestRecoveryEqualToPrincipalIsRejectedWithoutFinancialPosting() {
        policy(0, 0, "ZERO_INTEREST", "CONTRACT_RATE", "0");
        Long fdId = prematureFd(5);
        payPostedInterest(fdId, new BigDecimal("40.02"));
        BigDecimal before = balance();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> uplift(fdId));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(0, before.compareTo(balance()));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id=?",String.class,fdId));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_fd_premature_withdrawal WHERE fd_id=?",fdId));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key=?", "FD-PREMATURE:"+fdId));
    }
}
