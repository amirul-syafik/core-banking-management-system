package com.cbms;

import com.cbms.dto.BankFixedDepositPrematureWithdrawalRequest;
import com.cbms.dto.BankFixedDepositRenewalRequest;
import com.cbms.service.BankFixedDepositMaturityProcessingService;
import com.cbms.service.BankFixedDepositPrematureWithdrawalService;
import com.cbms.service.BankFixedDepositRenewalService;
import com.cbms.service.BankFixedDepositMaturitySettlementService;
import com.cbms.dto.BankFixedDepositMaturitySettlementRequest;
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
 * Round 3B: PostgreSQL-backed FD maturity and renewal lifecycle checks.
 * Only runs with the guarded corebank_lite_test financial fixture.
 * All writes (including the fixture) are rolled back per test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = {"/sql/financial-test-fixture.sql", "/sql/fd-maturity-settlement-transaction-type.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BankFixedDepositMaturitySettlementIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired BankFixedDepositMaturityProcessingService maturityService;
    @Autowired BankFixedDepositRenewalService renewalService;
    @Autowired BankFixedDepositMaturitySettlementService settlementService;
    @Autowired BankFixedDepositPrematureWithdrawalService upliftService;

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


    @Test
    void maturedFdSettlementCreditsPrincipalOnceWithBalancedLedger() {
        Long fdId = matureOneDayFd();
        BigDecimal before = balance();
        Map<String, Object> result = settlementService.settle(fdId,
                new BankFixedDepositMaturitySettlementRequest(accountId, "principal settlement"), ACTOR);
        assertEquals("CLOSED", result.get("status"));
        assertEquals(true, result.get("principalRepaid"));
        assertEquals(0, balance().subtract(before).compareTo(PRINCIPAL));
        assertEquals("CLOSED", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?", String.class, fdId));
        Long txId = ((Number) result.get("transactionId")).longValue();
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_transaction
                WHERE txn_id = ? AND txn_typ = 'FD_MATURITY_SETTLEMENT' AND txn_amt = ?
                  AND idempotency_key = ? AND txn_sts = 'POSTED'
                """, txId, PRINCIPAL, "FD-MATURITY-SETTLEMENT:" + fdId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id
                WHERE txn_id = ? AND gl_cd = '200200' AND entry_side = 'DEBIT'
                  AND acct_id IS NULL AND entry_amt = ?
                """, txId, PRINCIPAL));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id
                WHERE txn_id = ? AND gl_cd = '200100' AND entry_side = 'CREDIT'
                  AND acct_id = ? AND entry_amt = ?
                """, txId, accountId, PRINCIPAL));
    }

    @Test
    void duplicateSettlementDoesNotCreditPrincipalTwice() {
        Long fdId = matureOneDayFd();
        BankFixedDepositMaturitySettlementRequest request =
                new BankFixedDepositMaturitySettlementRequest(accountId, "test");
        settlementService.settle(fdId, request, ACTOR);
        BigDecimal balanceAfter = balance();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> settlementService.settle(fdId, request, ACTOR));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(0, balance().compareTo(balanceAfter));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?",
                "FD-MATURITY-SETTLEMENT:" + fdId));
    }

    @Test
    void settlingBeforeMaturityIsRejectedWithoutFinancialMutation() {
        Long fdId = fundedFd(LocalDate.now().minusDays(1), LocalDate.now(), true);
        BigDecimal before = balance();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> settlementService.settle(fdId,
                        new BankFixedDepositMaturitySettlementRequest(accountId, "early"), ACTOR));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(0, before.compareTo(balance()));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?",
                "FD-MATURITY-SETTLEMENT:" + fdId));
    }

    @Test
    void existingRenewalBlocksPrincipalSettlement() {
        Long fdId = matureOneDayFd();
        renewalService.renew(fdId,
                new BankFixedDepositRenewalRequest("PRINCIPAL_ONLY", schemeId, 1, "renew"), ACTOR);
        BigDecimal before = balance();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> settlementService.settle(fdId,
                        new BankFixedDepositMaturitySettlementRequest(accountId, "conflict"), ACTOR));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(0, before.compareTo(balance()));
    }

    @Test
    void settlementBlocksSubsequentRenewal() {
        Long fdId = matureOneDayFd();
        settlementService.settle(fdId,
                new BankFixedDepositMaturitySettlementRequest(accountId, "paid"), ACTOR);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> renewalService.renew(fdId,
                        new BankFixedDepositRenewalRequest("PRINCIPAL_ONLY", schemeId, 1, "too late"), ACTOR));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_renewal WHERE original_fd_id = ?", fdId));
    }

    @Test
    void unsettledInterestPreventsClosingMaturedDeposit() {
        Long fdId = matureOneDayFd();
        jdbc.update("""
                UPDATE public.bank_fixed_deposit_accrual
                SET accrued_interest = 1.00
                WHERE fd_id = ?
                """, fdId);
        BigDecimal before = balance();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> settlementService.settle(fdId,
                        new BankFixedDepositMaturitySettlementRequest(accountId, "unpaid"), ACTOR));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(0, before.compareTo(balance()));
        assertEquals("MATURED", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?", String.class, fdId));
    }
}
