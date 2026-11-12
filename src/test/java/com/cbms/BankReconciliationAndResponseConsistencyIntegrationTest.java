package com.cbms;

import com.cbms.dto.AccountReconciliationResponse;
import com.cbms.dto.BankPostingResponse;
import com.cbms.dto.TransferRequest;
import com.cbms.dto.TransferResponse;
import com.cbms.service.AccountReconciliationService;
import com.cbms.service.BankPostingService;
import com.cbms.service.BankTransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Financial integrity regression tests. All changes occur on corebank_lite_test
 * and are rolled back with the enclosing test transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = {"/sql/financial-test-fixture.sql", "/sql/financial-transfer-test-fixture.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BankReconciliationAndResponseConsistencyIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired AccountReconciliationService reconciliation;
    @Autowired BankPostingService postings;
    @Autowired BankTransferService transfers;

    private Long account(String accountNumber) {
        return jdbc.queryForObject("SELECT acct_id FROM public.bank_account WHERE acct_no = ?",
                Long.class, accountNumber);
    }

    private BigDecimal balance(Long accountId) {
        return jdbc.queryForObject("SELECT ledger_bal FROM public.bank_account_balance WHERE acct_id = ?",
                BigDecimal.class, accountId);
    }

    private static void money(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "Unexpected money amount");
    }

    private Authentication maker() {
        return new UsernamePasswordAuthenticationToken("round3-test-maker", null,
                List.of(new SimpleGrantedAuthority("ROLE_MAKER")));
    }

    @Test
    void untouchedFixtureIsReconciled() {
        Long acct = account("TEST-REV-900001");
        AccountReconciliationResponse result = reconciliation.reconcile(acct);
        assertTrue(result.reconciled());
        money("100.00", result.storedBalance());
        money("100.00", result.calculatedBalance());
        money("0.00", result.difference());
    }

    @Test
    void storedBalanceTamperingIsDetectedWithoutChangingBalance() {
        Long acct = account("TEST-REV-900001");
        jdbc.update("UPDATE public.bank_account_balance SET ledger_bal = 125.00 WHERE acct_id = ?", acct);
        AccountReconciliationResponse result = reconciliation.reconcile(acct);
        assertFalse(result.reconciled());
        money("125.00", result.storedBalance());
        money("100.00", result.calculatedBalance());
        money("25.00", result.difference());
        money("125.00", balance(acct)); // reconciliation must be read-only
    }

    @Test
    void missingCustomerDepositLedgerEntryIsDetected() {
        Long acct = account("TEST-REV-900001");
        int deleted = jdbc.update("""
                DELETE FROM public.bank_ledger_entry
                WHERE acct_id = ? AND gl_id = (
                    SELECT gl_id FROM public.bank_ledger_account WHERE gl_cd = '200100'
                )
                """, acct);
        assertEquals(1, deleted, "Fixture should contain one customer deposit GL entry");
        AccountReconciliationResponse result = reconciliation.reconcile(acct);
        assertFalse(result.reconciled());
        money("100.00", result.storedBalance());
        money("0.00", result.calculatedBalance());
        money("100.00", result.difference());
    }

    @Test
    void repeatedDepositReturnsOriginalTransactionButBalancesAtRetryTime() {
        Long acct = account("TEST-LIFE-A");
        String key = UUID.randomUUID().toString();
        BankPostingResponse original = postings.post(acct, "DEPOSIT", new BigDecimal("10.00"), key, maker());
        postings.post(acct, "DEPOSIT", new BigDecimal("5.00"), UUID.randomUUID().toString(), maker());
        BankPostingResponse retry = postings.post(acct, "DEPOSIT", new BigDecimal("10.0"), key, maker());

        assertEquals(original.txnId(), retry.txnId());
        assertEquals(original.txnRef(), retry.txnRef());
        money("10.00", original.ledgerBalance());
        money("15.00", retry.ledgerBalance()); // current, not original post-transaction balance
        money("15.00", retry.availableBalance());
        money("15.00", balance(acct));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?", Integer.class, key));
    }

    @Test
    void repeatedTransferReturnsOriginalTransactionButCurrentAccountBalances() {
        Long a = account("TEST-LIFE-A");
        Long b = account("TEST-LIFE-B");
        postings.post(a, "DEPOSIT", new BigDecimal("30.00"), UUID.randomUUID().toString(), maker());
        String key = UUID.randomUUID().toString();
        TransferResponse original = transfers.transfer(a, new TransferRequest(b, new BigDecimal("10.00")), key, maker());
        postings.post(a, "DEPOSIT", new BigDecimal("5.00"), UUID.randomUUID().toString(), maker());
        TransferResponse retry = transfers.transfer(a, new TransferRequest(b, new BigDecimal("10.0")), key, maker());

        assertEquals(original.txnId(), retry.txnId());
        assertEquals(original.txnRef(), retry.txnRef());
        money("20.00", original.sourceBalance());
        money("10.00", original.destinationBalance());
        money("25.00", retry.sourceBalance()); // current source balance
        money("10.00", retry.destinationBalance());
        money("25.00", balance(a));
        money("10.00", balance(b));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?", Integer.class, key));
    }

    @Test
    void postedTransferIsBalancedAndBothAccountsReconcile() {
        Long a = account("TEST-LIFE-A");
        Long b = account("TEST-LIFE-B");
        postings.post(a, "DEPOSIT", new BigDecimal("40.00"), UUID.randomUUID().toString(), maker());
        TransferResponse transfer = transfers.transfer(a, new TransferRequest(b, new BigDecimal("17.00")),
                UUID.randomUUID().toString(), maker());

        Integer entries = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.bank_ledger_entry WHERE txn_id = ?", Integer.class, transfer.txnId());
        BigDecimal debitMinusCredit = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN entry_side = 'DEBIT' THEN entry_amt
                                         WHEN entry_side = 'CREDIT' THEN -entry_amt ELSE 0 END), 0)
                FROM public.bank_ledger_entry WHERE txn_id = ?
                """, BigDecimal.class, transfer.txnId());
        assertEquals(2, entries);
        money("0.00", debitMinusCredit);
        assertTrue(reconciliation.reconcile(a).reconciled());
        assertTrue(reconciliation.reconcile(b).reconciled());
        money("23.00", balance(a));
        money("17.00", balance(b));
    }
}
