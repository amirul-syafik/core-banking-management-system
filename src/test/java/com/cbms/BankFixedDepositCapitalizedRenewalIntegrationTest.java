package com.cbms;

import com.cbms.dto.BankFixedDepositPrematureWithdrawalRequest;
import com.cbms.dto.BankFixedDepositRenewalRequest;
import com.cbms.service.BankFixedDepositMaturityProcessingService;
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
class BankFixedDepositCapitalizedRenewalIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired BankFixedDepositMaturityProcessingService maturityService;
    @Autowired BankFixedDepositRenewalService renewalService;
    @Autowired com.cbms.service.BankFixedDepositInterestPayoutService payoutService;

    private Long accountId;
    private Long productId;
    private Long schemeId;
    private static final String ACTOR = "FD_CAP_RENEWAL_TEST";
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


    private void postUnpaidInterest(Long fdId, BigDecimal interest) {
        jdbc.update("""
                INSERT INTO public.bank_ledger_account(gl_cd, gl_nm, gl_typ, ccy_cd, act_flg)
                VALUES ('200300', 'FD payable test', 'LIABILITY', 'MYR', TRUE)
                ON CONFLICT (gl_cd) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO public.bank_ledger_account(gl_cd, gl_nm, gl_typ, ccy_cd, act_flg)
                VALUES ('500100', 'FD interest expense test', 'EXPENSE', 'MYR', TRUE)
                ON CONFLICT (gl_cd) DO NOTHING
                """);
        String ref = "TXN-FD-CAP-" + UUID.randomUUID();
        Long id = jdbc.queryForObject("""
                INSERT INTO public.bank_transaction
                 (txn_ref, acct_id, txn_typ, txn_amt, ccy_cd, txn_sts, idempotency_key, crt_by)
                VALUES (?, ?, 'FD_INTEREST_ACCRUAL', ?, 'MYR', 'POSTED', ?, ?)
                RETURNING txn_id
                """, Long.class, ref, accountId, interest, "FD-CAP:" + ref, ACTOR);
        jdbc.update("""
                INSERT INTO public.bank_ledger_entry(txn_id, gl_id, acct_id, entry_side, entry_amt)
                SELECT ?, gl_id, NULL, 'DEBIT', ? FROM public.bank_ledger_account WHERE gl_cd = '500100'
                """, id, interest);
        jdbc.update("""
                INSERT INTO public.bank_ledger_entry(txn_id, gl_id, acct_id, entry_side, entry_amt)
                SELECT ?, gl_id, NULL, 'CREDIT', ? FROM public.bank_ledger_account WHERE gl_cd = '200300'
                """, id, interest);
        assertEquals(1, jdbc.update("""
                UPDATE public.bank_fixed_deposit_accrual
                SET accrued_interest = ?, cumulative_interest = ?, posting_ref = ?
                WHERE fd_id = ?
                """, interest, interest, ref, fdId));
    }

    @Test
    void capitalizedRenewalMovesPrincipalAndUnpaidInterestToNewLiability() {
        Long originalId = fundedFd(LocalDate.now().minusDays(1), LocalDate.now(), true);
        postUnpaidInterest(originalId, new BigDecimal("1.25"));
        assertEquals("MATURED", maturityService.process(originalId, ACTOR).get("status"));
        BigDecimal before = balance();
        Map<String, Object> result = renewalService.renew(originalId,
                new BankFixedDepositRenewalRequest("PRINCIPAL_PLUS_INTEREST", schemeId, 1, "capitalize"), ACTOR);
        Long renewedId = ((Number) result.get("renewedFdId")).longValue();
        assertEquals(0, new BigDecimal("1.25").compareTo((BigDecimal) result.get("capitalizedInterest")));
        assertEquals(0, new BigDecimal("41.25").compareTo((BigDecimal) result.get("renewalAmount")));
        assertEquals(0, new BigDecimal("41.25").compareTo(jdbc.queryForObject(
                "SELECT principal_amt FROM public.bank_fixed_deposit WHERE fd_id = ?", BigDecimal.class, renewedId)));
        assertEquals(0, before.compareTo(balance()), "No savings account movement during internal FD renewal");
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_renewal WHERE original_fd_id = ? AND renewal_typ = 'PRINCIPAL_PLUS_INTEREST' AND capitalized_interest = 1.25", originalId));
        Long txId = jdbc.queryForObject("SELECT txn_id FROM public.bank_transaction WHERE idempotency_key = ?", Long.class, "FD-RENEWAL:" + originalId);
        assertEquals(3, count("SELECT COUNT(*) FROM public.bank_ledger_entry WHERE txn_id = ?", txId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e JOIN public.bank_ledger_account g ON g.gl_id=e.gl_id
                WHERE e.txn_id=? AND g.gl_cd='200200' AND e.entry_side='DEBIT' AND e.entry_amt=40.00
                """, txId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e JOIN public.bank_ledger_account g ON g.gl_id=e.gl_id
                WHERE e.txn_id=? AND g.gl_cd='200300' AND e.entry_side='DEBIT' AND e.entry_amt=1.25
                """, txId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e JOIN public.bank_ledger_account g ON g.gl_id=e.gl_id
                WHERE e.txn_id=? AND g.gl_cd='200200' AND e.entry_side='CREDIT' AND e.entry_amt=41.25
                """, txId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_fixed_deposit_placement
                WHERE fd_id = ? AND funding_typ='FD_RENEWAL' AND placement_amt=41.25
                """, renewedId));
        assertEquals(0, payoutService.unpaidInterest(originalId).signum());
    }

    @Test
    void capitalizedRenewalCannotBeRepeatedOrPaidOutFromOriginalFd() {
        Long originalId = fundedFd(LocalDate.now().minusDays(1), LocalDate.now(), true);
        postUnpaidInterest(originalId, new BigDecimal("1.25"));
        assertEquals("MATURED", maturityService.process(originalId, ACTOR).get("status"));
        var request = new BankFixedDepositRenewalRequest("PRINCIPAL_PLUS_INTEREST", schemeId, 1, "once");
        renewalService.renew(originalId, request, ACTOR);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> renewalService.renew(originalId, request, ACTOR)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> payoutService.payout(originalId,
                     new com.cbms.dto.BankFixedDepositInterestPayoutRequest(accountId), actor)).getStatusCode());
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_renewal WHERE original_fd_id = ?", originalId));
    }

    @Test
    void principalOnlyRenewalRejectsStrandedUnpaidInterest() {
        Long originalId = fundedFd(LocalDate.now().minusDays(1), LocalDate.now(), true);
        postUnpaidInterest(originalId, new BigDecimal("1.25"));
        assertEquals("MATURED", maturityService.process(originalId, ACTOR).get("status"));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> renewalService.renew(originalId,
                new BankFixedDepositRenewalRequest("PRINCIPAL_ONLY", schemeId, 1, "no orphan"), ACTOR)).getStatusCode());
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_renewal WHERE original_fd_id = ?", originalId));
    }

    @Test
    void capitalizedRenewalRequiresPositiveUnpaidInterest() {
        Long originalId = matureOneDayFd();
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> renewalService.renew(originalId,
                new BankFixedDepositRenewalRequest("PRINCIPAL_PLUS_INTEREST", schemeId, 1, "no interest"), ACTOR)).getStatusCode());
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_fixed_deposit_renewal WHERE original_fd_id = ?", originalId));
    }
}
