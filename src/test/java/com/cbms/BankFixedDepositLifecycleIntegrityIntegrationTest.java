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
 * Round 3B: PostgreSQL-backed FD maturity and renewal lifecycle checks.
 * Only runs with the guarded corebank_lite_test financial fixture.
 * All writes (including the fixture) are rolled back per test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = "/sql/financial-test-fixture.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BankFixedDepositLifecycleIntegrityIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired BankFixedDepositMaturityProcessingService maturityService;
    @Autowired BankFixedDepositRenewalService renewalService;
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
    void maturityRecordsStatusWithoutRepayingPrincipalOrPostingAnotherTransaction() {
        Long fdId = fundedFd(LocalDate.now().minusDays(1), LocalDate.now(), true);
        BigDecimal before = balance();
        int transactionsBefore = count("SELECT COUNT(*) FROM public.bank_transaction WHERE acct_id = ?", accountId);
        Map<String, Object> response = maturityService.process(fdId, ACTOR);
        assertEquals("MATURED", response.get("status"));
        assertEquals(false, response.get("principalRepaid"));
        assertEquals("MATURED", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?", String.class, fdId));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_maturity WHERE fd_id = ?", fdId));
        assertEquals(0, before.compareTo(balance()), "Maturity must not credit the source account");
        assertEquals(transactionsBefore,
                count("SELECT COUNT(*) FROM public.bank_transaction WHERE acct_id = ?", accountId));
    }

    @Test
    void secondMaturityProcessingDoesNotCreateAnotherMaturityRecord() {
        Long fdId = matureOneDayFd();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> maturityService.process(fdId, ACTOR));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_maturity WHERE fd_id = ?", fdId));
    }

    @Test
    void maturityRejectsIncompleteAccrualsWithoutStatusChange() {
        Long fdId = fundedFd(LocalDate.now().minusDays(1), LocalDate.now(), false);
        BigDecimal before = balance();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> maturityService.process(fdId, ACTOR));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?", String.class, fdId));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_maturity WHERE fd_id = ?", fdId));
        assertEquals(0, before.compareTo(balance()));
    }

    @Test
    void principalOnlyRenewalMovesLiabilityWithoutCreditingSourceAccount() {
        Long fdId = matureOneDayFd();
        BigDecimal before = balance();
        Map<String, Object> renewed = renewalService.renew(fdId,
                new BankFixedDepositRenewalRequest("PRINCIPAL_ONLY", schemeId, 1, "test renewal"),
                ACTOR);
        Long newId = ((Number) renewed.get("renewedFdId")).longValue();
        assertNotEquals(fdId, newId);
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?", String.class, newId));
        assertEquals("MATURED", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?", String.class, fdId));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_renewal WHERE original_fd_id = ?", fdId));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_placement WHERE fd_id = ? AND funding_typ = 'FD_RENEWAL'", newId));
        assertEquals(0, before.compareTo(balance()), "Internal renewal must not withdraw or credit savings funds");
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_transaction t
                JOIN public.bank_ledger_entry d ON d.txn_id = t.txn_id
                JOIN public.bank_ledger_entry c ON c.txn_id = t.txn_id
                JOIN public.bank_ledger_account gd ON gd.gl_id = d.gl_id
                JOIN public.bank_ledger_account gc ON gc.gl_id = c.gl_id
                WHERE t.idempotency_key = ? AND t.txn_typ = 'FD_RENEWAL'
                  AND d.entry_side = 'DEBIT' AND c.entry_side = 'CREDIT'
                  AND d.entry_amt = ? AND c.entry_amt = ?
                  AND gd.gl_cd = '200200' AND gc.gl_cd = '200200'
                """, "FD-RENEWAL:" + fdId, PRINCIPAL, PRINCIPAL));
    }

    @Test
    void secondRenewalIsRejectedWithoutCreatingAnotherDeposit() {
        Long fdId = matureOneDayFd();
        BankFixedDepositRenewalRequest request = new BankFixedDepositRenewalRequest(
                "PRINCIPAL_ONLY", schemeId, 1, "duplicate renewal");
        renewalService.renew(fdId, request, ACTOR);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> renewalService.renew(fdId, request, ACTOR));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_renewal WHERE original_fd_id = ?", fdId));
    }

    @Test
    void prematureUpliftIsRejectedAfterMaturityWithoutSettlementPosting() {
        Long fdId = matureOneDayFd();
        int prior = count("SELECT COUNT(*) FROM public.bank_transaction WHERE acct_id = ?", accountId);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> upliftService.withdraw(fdId,
                        new BankFixedDepositPrematureWithdrawalRequest(accountId, "late uplift"), actor));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_fd_premature_withdrawal WHERE fd_id = ?", fdId));
        assertEquals(prior, count("SELECT COUNT(*) FROM public.bank_transaction WHERE acct_id = ?", accountId));
    }
}
