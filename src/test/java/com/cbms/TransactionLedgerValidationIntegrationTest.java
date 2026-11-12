package com.cbms;

import com.cbms.dto.TransferRequest;
import com.cbms.dto.TransactionReversalRequest;
import com.cbms.service.BankPostingService;
import com.cbms.service.BankTransferService;
import com.cbms.service.BankTransactionReversalService;
import com.cbms.service.TransactionLedgerValidationService;
import jakarta.persistence.EntityManager;
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

/** All changes are rolled back in corebank_lite_test; never run fixture against production. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = {"/sql/financial-test-fixture.sql", "/sql/financial-transfer-test-fixture.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TransactionLedgerValidationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired TransactionLedgerValidationService validator;
    @Autowired BankPostingService posting;
    @Autowired BankTransferService transfers;
    @Autowired BankTransactionReversalService reversals;

    private final Authentication actor = new UsernamePasswordAuthenticationToken("round5-maker", null,
            List.of(new SimpleGrantedAuthority("ROLE_MAKER")));

    private Long acct(String number) {
        return jdbc.queryForObject("SELECT acct_id FROM public.bank_account WHERE acct_no = ?", Long.class, number);
    }

    private String key() { return UUID.randomUUID().toString(); }

    private Long deposit(Long account, String amount) {
        return posting.post(account, "DEPOSIT", new BigDecimal(amount), key(), actor).txnId();
    }

    private Long withdrawal(Long account, String amount) {
        return posting.post(account, "WITHDRAWAL", new BigDecimal(amount), key(), actor).txnId();
    }

    private Long transfer(Long from, Long to, String amount) {
        return transfers.transfer(from, new TransferRequest(to, new BigDecimal(amount)), key(), actor).txnId();
    }

    private Long reverse(Long txn) {
        return reversals.reverse(txn, new TransactionReversalRequest("Round 5 verification"),
                key(), actor).reversalTxnId();
    }

    private void valid(Long txn) {
        var result = validator.validate(txn);
        assertTrue(result.valid(), () -> "Expected valid: " + result.findings());
        assertTrue(result.findings().isEmpty());
    }

    private void invalid(Long txn) {
        // JdbcTemplate corrupts ledger rows directly in negative tests. Flush and
        // evict entities previously managed by Hibernate so validation reads the
        // changed database state rather than stale first-level cached entities.
        entityManager.flush();
        entityManager.clear();
        var result = validator.validate(txn);
        assertFalse(result.valid(), "Corrupted financial ledger must be rejected");
        assertFalse(result.findings().isEmpty());
    }

    @Test void validDepositAndWithdrawalPass() {
        Long account = acct("TEST-LIFE-A");
        valid(deposit(account, "70.00"));
        valid(withdrawal(account, "20.00"));
    }

    @Test void validTransferPasses() {
        Long a = acct("TEST-LIFE-A"), b = acct("TEST-LIFE-B");
        deposit(a, "100.00");
        valid(transfer(a, b, "35.00"));
    }

    @Test void depositReversalPasses() {
        Long a = acct("TEST-LIFE-A");
        Long original = deposit(a, "35.00");
        valid(reverse(original));
    }

    @Test void withdrawalReversalPasses() {
        Long a = acct("TEST-LIFE-A");
        deposit(a, "100.00");
        Long original = withdrawal(a, "20.00");
        valid(reverse(original));
    }

    @Test void transferReversalPasses() {
        Long a = acct("TEST-LIFE-A"), b = acct("TEST-LIFE-B");
        deposit(a, "100.00");
        Long original = transfer(a, b, "35.00");
        valid(reverse(original));
    }

    @Test void missingCashLedgerEntryIsDetected() {
        Long txn = deposit(acct("TEST-LIFE-A"), "25.00");
        assertEquals(1, jdbc.update("DELETE FROM public.bank_ledger_entry WHERE txn_id = ? AND entry_side = 'DEBIT'", txn));
        invalid(txn);
    }

    @Test void balancedDuplicateEntriesAreDetected() {
        Long txn = deposit(acct("TEST-LIFE-A"), "25.00");
        assertEquals(2, jdbc.update("""
                INSERT INTO public.bank_ledger_entry (txn_id, gl_id, acct_id, entry_side, entry_amt, crt_dt)
                SELECT txn_id, gl_id, acct_id, entry_side, entry_amt, crt_dt
                FROM public.bank_ledger_entry WHERE txn_id = ?
                """, txn));
        invalid(txn);
    }

    @Test void wrongGlOrAmountIsDetected() {
        Long txn = withdrawal(acct("TEST-REV-900001"), "10.00");
        assertEquals(1, jdbc.update("""
                UPDATE public.bank_ledger_entry SET gl_id =
                (SELECT gl_id FROM public.bank_ledger_account WHERE gl_cd = '100100')
                WHERE txn_id = ? AND entry_side = 'DEBIT'
                """, txn));
        invalid(txn);
    }

    @Test void modifiedReversalAmountIsDetected() {
        Long a = acct("TEST-LIFE-A");
        Long original = deposit(a, "30.00");
        Long reversal = reverse(original);
        assertEquals(1, jdbc.update("UPDATE public.bank_transaction SET txn_amt = 35.00 WHERE txn_id = ?", reversal));
        entityManager.flush();
        entityManager.clear(); // Ensure validation reads the tampered database row, not a cached JPA entity.
        invalid(reversal);
    }

    @Test void validationNeverUpdatesFinancialRecords() {
        Long txn = deposit(acct("TEST-LIFE-A"), "25.00");
        Long acct = acct("TEST-LIFE-A");
        BigDecimal before = jdbc.queryForObject("SELECT ledger_bal FROM public.bank_account_balance WHERE acct_id = ?", BigDecimal.class, acct);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM public.bank_ledger_entry WHERE txn_id = ?", Integer.class, txn);
        valid(txn);
        assertEquals(0, before.compareTo(jdbc.queryForObject("SELECT ledger_bal FROM public.bank_account_balance WHERE acct_id = ?", BigDecimal.class, acct)));
        assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM public.bank_ledger_entry WHERE txn_id = ?", Integer.class, txn));
    }
}
