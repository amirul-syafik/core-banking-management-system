package com.cbms;

import com.cbms.dto.BankFixedDepositPlacementRequest;
import com.cbms.service.BankFixedDepositAccrualService;
import com.cbms.service.BankFixedDepositPlacementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL integration tests for placement funding and daily interest posting.
 * The fixture and every test's changes are rolled back by Spring's test transaction.
 * NEVER run against a non-test database: the SQL fixture resets its dedicated customer.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "/sql/financial-test-fixture.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BankFixedDepositOperationalIntegrityIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private BankFixedDepositPlacementService placementService;
    @Autowired private BankFixedDepositAccrualService accrualService;

    private Long accountId;
    private Long productId;
    private Long schemeId;
    private final Authentication actor = new UsernamePasswordAuthenticationToken(
            "fd-integration-test", null, List.of());

    @BeforeEach
    void prepare() {
        accountId = jdbc.queryForObject(
                "SELECT acct_id FROM public.bank_account WHERE acct_no = 'TEST-REV-900001'",
                Long.class);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        productId = jdbc.queryForObject("""
                INSERT INTO public.bank_product
                  (prod_cd, prod_nm, prod_cat, banking_typ, prod_sts, crt_by)
                VALUES (?, 'FD integration test product', 'FIXED_DEPOSIT',
                        'CONVENTIONAL', 'ACTIVE', 'INTEGRATION_TEST')
                RETURNING prod_id
                """, Long.class, "FDIT" + suffix);
        schemeId = jdbc.queryForObject("""
                INSERT INTO public.bank_scheme
                  (prod_id, schm_cd, schm_nm, min_amt, max_amt,
                   min_tenor, max_tenor, schm_sts, crt_by)
                VALUES (?, ?, 'FD integration test scheme', 1.00, 100000.00,
                        1, 12, 'ACTIVE', 'INTEGRATION_TEST')
                RETURNING schm_id
                """, Long.class, productId, "FDS" + suffix);
        for (String[] gl : new String[][] {
                {"200200", "Fixed deposit liability", "LIABILITY"},
                {"500100", "FD interest expense", "EXPENSE"},
                {"200300", "FD interest payable", "LIABILITY"}
        }) {
            jdbc.update("""
                    INSERT INTO public.bank_ledger_account
                      (gl_cd, gl_nm, gl_typ, ccy_cd, act_flg)
                    VALUES (?, ?, ?, 'MYR', TRUE)
                    ON CONFLICT (gl_cd) DO NOTHING
                    """, gl[0], gl[1], gl[2]);
        }
    }

    private Long fd(BigDecimal principal, LocalDate date, String status) {
        return jdbc.queryForObject("""
                INSERT INTO public.bank_fixed_deposit
                  (fd_ref, cust_id, prod_id, schm_id, fd_typ,
                   principal_amt, tenor_mth, placement_dt, maturity_dt,
                   interest_rate, fd_sts, crt_by)
                VALUES (?, -900001, ?, ?, 'FIXED_DEPOSIT',
                        ?, 1, ?, ?, 36.500000, ?, 'INTEGRATION_TEST')
                RETURNING fd_id
                """, Long.class,
                "FD-INT-" + UUID.randomUUID(), productId, schemeId,
                principal, date, date.plusMonths(1), status);
    }

    private BigDecimal balance() {
        return jdbc.queryForObject(
                "SELECT ledger_bal FROM public.bank_account_balance WHERE acct_id = ?",
                BigDecimal.class, accountId);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private String place(Long fdId) {
        String reference = "FD-TEST-" + UUID.randomUUID();
        placementService.place(fdId,
                new BankFixedDepositPlacementRequest(accountId, reference, "integration test"),
                actor);
        return reference;
    }

    @Test
    void placementDebitsSourceAndCreatesExactlyTwoBalancedEntries() {
        Long fdId = fd(new BigDecimal("40.00"), LocalDate.now(), "DRAFT");
        String reference = place(fdId);
        assertEquals(0, new BigDecimal("60.00").compareTo(balance()));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?", String.class, fdId));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_placement WHERE fd_id = ?", fdId));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?", "FD:" + reference));
        assertEquals(2, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_transaction t ON t.txn_id = e.txn_id
                WHERE t.idempotency_key = ?
                """, "FD:" + reference));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_transaction t ON t.txn_id = e.txn_id
                JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id
                WHERE t.idempotency_key = ? AND g.gl_cd = '200100'
                  AND e.entry_side = 'DEBIT' AND e.acct_id = ? AND e.entry_amt = 40
                """, "FD:" + reference, accountId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_transaction t ON t.txn_id = e.txn_id
                JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id
                WHERE t.idempotency_key = ? AND g.gl_cd = '200200'
                  AND e.entry_side = 'CREDIT' AND e.acct_id IS NULL AND e.entry_amt = 40
                """, "FD:" + reference));
    }

    @Test
    void insufficientFundsDoesNotWritePlacementOrFundingTransaction() {
        Long fdId = fd(new BigDecimal("150.00"), LocalDate.now(), "DRAFT");
        String reference = "FD-TEST-" + UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> placementService.place(fdId,
                        new BankFixedDepositPlacementRequest(accountId, reference, null), actor));
        assertEquals(0, new BigDecimal("100.00").compareTo(balance()));
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?", String.class, fdId));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_placement WHERE fd_id = ?", fdId));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?", "FD:" + reference));
    }

    @Test
    void secondPlacementOfActiveDepositDoesNotPostAgain() {
        Long fdId = fd(new BigDecimal("40.00"), LocalDate.now(), "DRAFT");
        String firstReference = place(fdId);
        assertThrows(ResponseStatusException.class,
                () -> placementService.place(fdId,
                        new BankFixedDepositPlacementRequest(accountId,
                                "FD-RETRY-" + UUID.randomUUID(), null), actor));
        assertEquals(0, new BigDecimal("60.00").compareTo(balance()));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_placement WHERE fd_id = ?", fdId));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?", "FD:" + firstReference));
    }

    private Long preparedActiveDeposit(int daysAgo) {
        LocalDate placementDate = LocalDate.now().minusDays(daysAgo);
        Long fdId = fd(new BigDecimal("100.00"), placementDate, "ACTIVE");
        jdbc.update("""
                INSERT INTO public.bank_fixed_deposit_placement
                  (placement_ref, fd_id, source_acct_id, placement_amt,
                   placement_dt, posting_ref, crt_by)
                VALUES (?, ?, ?, 100.00, ?, ?, 'INTEGRATION_TEST')
                """, "FD-EXISTING-" + UUID.randomUUID(), fdId, accountId,
                placementDate, "FD-FUND-" + UUID.randomUUID());
        return fdId;
    }

    @Test
    void completedDayAccrualCreatesBalancedExpenseAndPayable() {
        Long fdId = preparedActiveDeposit(1);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        accrualService.accrue(fdId, yesterday, actor);
        BigDecimal accrued = jdbc.queryForObject("""
                SELECT accrued_interest FROM public.bank_fixed_deposit_accrual
                WHERE fd_id = ? AND accrual_dt = ?
                """, BigDecimal.class, fdId, yesterday);
        assertEquals(0, new BigDecimal("0.10").compareTo(accrued));
        String postingRef = jdbc.queryForObject("""
                SELECT posting_ref FROM public.bank_fixed_deposit_accrual
                WHERE fd_id = ? AND accrual_dt = ?
                """, String.class, fdId, yesterday);
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_transaction WHERE txn_ref = ? AND txn_typ = 'FD_INTEREST_ACCRUAL'", postingRef));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_transaction t ON t.txn_id = e.txn_id
                JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id
                WHERE t.txn_ref = ? AND g.gl_cd = '500100' AND e.entry_side = 'DEBIT'
                  AND e.entry_amt = 0.10 AND e.acct_id IS NULL
                """, postingRef));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_transaction t ON t.txn_id = e.txn_id
                JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id
                WHERE t.txn_ref = ? AND g.gl_cd = '200300' AND e.entry_side = 'CREDIT'
                  AND e.entry_amt = 0.10 AND e.acct_id IS NULL
                """, postingRef));
        assertEquals(0, new BigDecimal("100.00").compareTo(balance()),
                "Accrual must not credit source account before payout");
    }

    @Test
    void duplicateAccrualDateCannotPostTwice() {
        Long fdId = preparedActiveDeposit(1);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        accrualService.accrue(fdId, yesterday, actor);
        assertThrows(ResponseStatusException.class,
                () -> accrualService.accrue(fdId, yesterday, actor));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_accrual WHERE fd_id = ?", fdId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_transaction t
                JOIN public.bank_fixed_deposit_accrual a ON a.posting_ref = t.txn_ref
                WHERE a.fd_id = ? AND t.txn_typ = 'FD_INTEREST_ACCRUAL'
                """, fdId));
    }

    @Test
    void accrualCannotSkipEarliestUnprocessedDay() {
        Long fdId = preparedActiveDeposit(2);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertThrows(ResponseStatusException.class,
                () -> accrualService.accrue(fdId, yesterday, actor));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_accrual WHERE fd_id = ?", fdId));
    }
}
