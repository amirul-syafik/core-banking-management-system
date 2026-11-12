package com.cbms;

import com.cbms.service.AccountReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transaction-level GL integrity tests, using one controlled deposit fixture.
 * The integrity predicate below is TEST-ONLY: these tests do not add a new
 * production reconciliation endpoint or an automatic ledger integrity monitor.
 * Changes are rolled back after each test on corebank_lite_test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "/sql/financial-test-fixture.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BankTransactionLedgerCompletenessIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired AccountReconciliationService reconciliation;

    private Long transactionId() {
        return jdbc.queryForObject(
                "SELECT txn_id FROM public.bank_transaction WHERE txn_ref = 'TEST-DEP-900001'",
                Long.class);
    }

    private Long accountId() {
        return jdbc.queryForObject(
                "SELECT acct_id FROM public.bank_account WHERE acct_no = 'TEST-REV-900001'",
                Long.class);
    }

    /**
     * For a standard MYR DEPOSIT, demand exactly one cash debit and one
     * customer-deposit credit, each equal to the transaction amount.
     * Checks more than SUM(debit) == SUM(credit): count, GL, account and amount.
     */
    private boolean hasCompleteDepositLedger(Long txnId) {
        Integer valid = jdbc.queryForObject("""
                SELECT CASE WHEN
                    t.txn_typ = 'DEPOSIT' AND t.txn_sts = 'POSTED' AND t.ccy_cd = 'MYR'
                    AND COUNT(e.entry_id) = 2
                    AND COUNT(e.entry_id) FILTER (WHERE e.entry_side = 'DEBIT') = 1
                    AND COUNT(e.entry_id) FILTER (WHERE e.entry_side = 'CREDIT') = 1
                    AND COALESCE(SUM(e.entry_amt) FILTER (WHERE e.entry_side = 'DEBIT'), 0) = t.txn_amt
                    AND COALESCE(SUM(e.entry_amt) FILTER (WHERE e.entry_side = 'CREDIT'), 0) = t.txn_amt
                    AND COUNT(e.entry_id) FILTER (
                        WHERE e.entry_side = 'DEBIT' AND g.gl_cd = '100100'
                          AND e.acct_id IS NULL AND e.entry_amt = t.txn_amt) = 1
                    AND COUNT(e.entry_id) FILTER (
                        WHERE e.entry_side = 'CREDIT' AND g.gl_cd = '200100'
                          AND e.acct_id = t.acct_id AND e.entry_amt = t.txn_amt) = 1
                    THEN 1 ELSE 0 END
                FROM public.bank_transaction t
                LEFT JOIN public.bank_ledger_entry e ON e.txn_id = t.txn_id
                LEFT JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id
                WHERE t.txn_id = ?
                GROUP BY t.txn_id, t.txn_typ, t.txn_sts, t.ccy_cd, t.txn_amt, t.acct_id
                """, Integer.class, txnId);
        return Integer.valueOf(1).equals(valid);
    }

    private BigDecimal signedLedgerDifference(Long txnId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE
                    WHEN entry_side = 'DEBIT' THEN entry_amt
                    WHEN entry_side = 'CREDIT' THEN -entry_amt
                    ELSE 0 END), 0)
                FROM public.bank_ledger_entry WHERE txn_id = ?
                """, BigDecimal.class, txnId);
    }

    private static void money(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    @Test
    void intactDepositFixtureHasCompleteBalancedLedger() {
        Long txn = transactionId();
        assertTrue(hasCompleteDepositLedger(txn));
        money("0.00", signedLedgerDifference(txn));
        assertTrue(reconciliation.reconcile(accountId()).reconciled());
    }

    @Test
    void missingCashSideIsDetectedEvenWhenAccountBalanceReconciles() {
        Long txn = transactionId();
        assertEquals(1, jdbc.update("""
                DELETE FROM public.bank_ledger_entry
                WHERE txn_id = ? AND entry_side = 'DEBIT'
                """, txn));
        assertTrue(reconciliation.reconcile(accountId()).reconciled(),
                "Customer-deposit GL alone cannot detect a missing cash-side entry");
        assertFalse(hasCompleteDepositLedger(txn));
        money("-100.00", signedLedgerDifference(txn));
    }

    @Test
    void mismatchedCashSideAmountIsDetected() {
        Long txn = transactionId();
        assertEquals(1, jdbc.update("""
                UPDATE public.bank_ledger_entry SET entry_amt = 90.00
                WHERE txn_id = ? AND entry_side = 'DEBIT'
                """, txn));
        assertFalse(hasCompleteDepositLedger(txn));
        money("-10.00", signedLedgerDifference(txn));
        assertTrue(reconciliation.reconcile(accountId()).reconciled());
    }

    @Test
    void duplicatedBalancedPairIsDetectedDespiteZeroNetDifference() {
        Long txn = transactionId();
        assertEquals(2, jdbc.update("""
                INSERT INTO public.bank_ledger_entry
                    (txn_id, gl_id, acct_id, entry_side, entry_amt, crt_dt)
                SELECT txn_id, gl_id, acct_id, entry_side, entry_amt, crt_dt
                FROM public.bank_ledger_entry WHERE txn_id = ?
                """, txn));
        money("0.00", signedLedgerDifference(txn));
        assertFalse(hasCompleteDepositLedger(txn),
                "Balanced duplicate entries must not pass transaction integrity");
        assertFalse(reconciliation.reconcile(accountId()).reconciled());
    }

    @Test
    void balancedButIncorrectAmountsAreDetectedAgainstTransactionAmount() {
        Long txn = transactionId();
        assertEquals(2, jdbc.update("""
                UPDATE public.bank_ledger_entry SET entry_amt = 110.00
                WHERE txn_id = ?
                """, txn));
        money("0.00", signedLedgerDifference(txn));
        assertFalse(hasCompleteDepositLedger(txn),
                "Balanced debit and credit are insufficient if amount differs from original transaction");
    }

    @Test
    void wrongCashGlIsDetectedWithoutAccountBalanceMismatch() {
        Long txn = transactionId();
        assertEquals(1, jdbc.update("""
                UPDATE public.bank_ledger_entry
                SET gl_id = (SELECT gl_id FROM public.bank_ledger_account WHERE gl_cd = '200100')
                WHERE txn_id = ? AND entry_side = 'DEBIT'
                """, txn));
        money("0.00", signedLedgerDifference(txn));
        assertTrue(reconciliation.reconcile(accountId()).reconciled());
        assertFalse(hasCompleteDepositLedger(txn),
                "Balanced totals must not hide an incorrect GL assignment");
    }
}
