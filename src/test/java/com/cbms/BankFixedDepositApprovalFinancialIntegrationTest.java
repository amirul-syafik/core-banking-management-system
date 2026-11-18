package com.cbms;

import com.cbms.service.BankFixedDepositApprovalService;
import com.cbms.service.BankFixedDepositMaturityProcessingService;
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
 * Exercises the real FD approval service and settlement posting against the
 * isolated PostgreSQL test database. Each test rolls back its fixture writes.
 * Requires the FD approval migration to have been installed in the
 * corebank_lite_test database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(scripts = {"/sql/financial-test-fixture.sql", "/sql/fd-maturity-settlement-transaction-type.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BankFixedDepositApprovalFinancialIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BankFixedDepositApprovalService approvals;
    @Autowired BankFixedDepositMaturityProcessingService maturity;

    private static final BigDecimal PRINCIPAL = new BigDecimal("40.00");
    private Long accountId;
    private Long schemeId;

    private Authentication user(String name) {
        return new UsernamePasswordAuthenticationToken(name, "unused", List.of());
    }

    @BeforeEach
    void prepareProductAndAccount() {
        accountId = jdbc.queryForObject(
                "SELECT acct_id FROM public.bank_account WHERE acct_no='TEST-REV-900001'", Long.class);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long productId = jdbc.queryForObject("""
                INSERT INTO public.bank_product
                 (prod_cd,prod_nm,prod_cat,banking_typ,prod_sts,crt_by)
                VALUES (?,'FD approval test product','FIXED_DEPOSIT',
                        'CONVENTIONAL','ACTIVE','FD_APPROVAL_TEST')
                RETURNING prod_id
                """, Long.class, "FAP" + suffix);
        schemeId = jdbc.queryForObject("""
                INSERT INTO public.bank_scheme
                 (prod_id,schm_cd,schm_nm,min_amt,max_amt,min_tenor,max_tenor,schm_sts,crt_by)
                VALUES (?,?,'FD approval test scheme',1,100000,1,12,'ACTIVE','FD_APPROVAL_TEST')
                RETURNING schm_id
                """, Long.class, productId, "FAS" + suffix);
        jdbc.update("""
                INSERT INTO public.bank_scheme_rate
                 (schm_id,rate_typ,fixed_rate_pct,eff_dt,rate_sts,crt_by)
                VALUES (?,'FIXED',3.65000,?,'ACTIVE','FD_APPROVAL_TEST')
                """, schemeId, LocalDate.now().minusMonths(2));
        jdbc.update("""
                INSERT INTO public.bank_ledger_account (gl_cd,gl_nm,gl_typ,ccy_cd,act_flg)
                VALUES ('200200','FD liability','LIABILITY','MYR',TRUE)
                ON CONFLICT (gl_cd) DO NOTHING
                """);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private BigDecimal balance() {
        return jdbc.queryForObject(
                "SELECT ledger_bal FROM public.bank_account_balance WHERE acct_id=?",
                BigDecimal.class, accountId);
    }

    private Long maturedDeposit() {
        String suffix = UUID.randomUUID().toString();
        LocalDate placementDate = LocalDate.now().minusDays(1);
        Long fdId = jdbc.queryForObject("""
                INSERT INTO public.bank_fixed_deposit
                 (fd_ref,cust_id,prod_id,schm_id,fd_typ,principal_amt,
                  tenor_mth,placement_dt,maturity_dt,interest_rate,fd_sts,crt_by)
                SELECT ?, -900001,prod_id,?, 'FIXED_DEPOSIT',?,1,?,?,3.650000,'ACTIVE',
                       'FD_APPROVAL_TEST'
                FROM public.bank_scheme WHERE schm_id=?
                RETURNING fd_id
                """, Long.class, "FD-AP-" + suffix, schemeId, PRINCIPAL,
                placementDate, LocalDate.now(), schemeId);
        String txRef = "TXN-FD-AP-" + suffix;
        Long txnId = jdbc.queryForObject("""
                INSERT INTO public.bank_transaction
                 (txn_ref,acct_id,txn_typ,txn_amt,ccy_cd,txn_sts,idempotency_key,crt_by)
                VALUES (?,?,'FD_PLACEMENT',?,'MYR','POSTED',?,'FD_APPROVAL_TEST')
                RETURNING txn_id
                """, Long.class, txRef, accountId, PRINCIPAL, "FD-AP:" + suffix);
        jdbc.update("""
                INSERT INTO public.bank_ledger_entry (txn_id,gl_id,acct_id,entry_side,entry_amt)
                SELECT ?,gl_id,?,'DEBIT',? FROM public.bank_ledger_account WHERE gl_cd='200100'
                """, txnId, accountId, PRINCIPAL);
        jdbc.update("""
                INSERT INTO public.bank_ledger_entry (txn_id,gl_id,acct_id,entry_side,entry_amt)
                SELECT ?,gl_id,NULL,'CREDIT',? FROM public.bank_ledger_account WHERE gl_cd='200200'
                """, txnId, PRINCIPAL);
        jdbc.update("""
                UPDATE public.bank_account_balance
                SET ledger_bal=ledger_bal-?,avail_bal=avail_bal-? WHERE acct_id=?
                """, PRINCIPAL, PRINCIPAL, accountId);
        jdbc.update("""
                INSERT INTO public.bank_fixed_deposit_placement
                 (placement_ref,fd_id,source_acct_id,placement_amt,placement_dt,
                  posting_ref,placement_sts,funding_typ,crt_by)
                VALUES (?,?,?,?,?,?,'POSTED','FD_PLACEMENT','FD_APPROVAL_TEST')
                """, "FD-P-AP-" + suffix, fdId, accountId, PRINCIPAL, placementDate, txRef);
        jdbc.update("""
                INSERT INTO public.bank_fixed_deposit_accrual
                 (fd_id,accrual_dt,daily_interest,accrued_interest,cumulative_interest,
                  posting_ref,accrual_sts,crt_by)
                VALUES (?,?,0.004000000000,0.00,0.00,?,'POSTED','FD_APPROVAL_TEST')
                """, fdId, placementDate, "FD-A-AP-" + suffix);
        assertEquals("MATURED", maturity.process(fdId, "FD_APPROVAL_TEST").get("status"));
        return fdId;
    }

    private Long submit(Long fdId, String maker) {
        Map<String,Object> result = approvals.submit(fdId, "MATURITY_SETTLEMENT",
                Map.of("destinationAccountId", accountId, "remarks", "approved settlement"),
                "Customer requested maturity settlement", user(maker));
        return ((Number) result.get("requestId")).longValue();
    }

    private String requestStatus(Long requestId) {
        return jdbc.queryForObject("""
                SELECT request_status FROM public.bank_fixed_deposit_approval_request
                WHERE request_id=?
                """, String.class, requestId);
    }

    @Test
    void makerSubmissionCreatesPendingRequestWithoutPayingPrincipal() {
        Long fdId = maturedDeposit();
        BigDecimal before = balance();
        Long requestId = submit(fdId, "maker_a");
        assertEquals("PENDING", requestStatus(requestId));
        assertEquals(0, before.compareTo(balance()));
        assertEquals("MATURED", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id=?", String.class, fdId));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key=?",
                "FD-MATURITY-SETTLEMENT:" + fdId));
    }

    @Test
    void differentCheckerApprovalPostsPrincipalExactlyOnceAndRecordsAudit() {
        Long fdId = maturedDeposit();
        BigDecimal before = balance();
        Long requestId = submit(fdId, "maker_a");
        Map<String,Object> result = approvals.decide(requestId, true, "Checked balances", user("checker_b"));
        assertEquals("APPROVED", result.get("status"));
        assertEquals(0, balance().subtract(before).compareTo(PRINCIPAL));
        assertEquals("CLOSED", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id=?", String.class, fdId));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key=?",
                "FD-MATURITY-SETTLEMENT:" + fdId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_fixed_deposit_approval_request
                WHERE request_id=? AND request_status='APPROVED'
                  AND requested_by='maker_a' AND decided_by='checker_b'
                  AND decided_at IS NOT NULL AND posting_result IS NOT NULL
                """, requestId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM (
                    SELECT t.txn_id FROM public.bank_transaction t
                    JOIN public.bank_ledger_entry e ON e.txn_id=t.txn_id
                    WHERE t.idempotency_key=?
                    GROUP BY t.txn_id
                    HAVING COUNT(*)=2
                       AND SUM(CASE WHEN e.entry_side='DEBIT' THEN e.entry_amt ELSE -e.entry_amt END)=0
                ) balanced_transactions
                """, "FD-MATURITY-SETTLEMENT:" + fdId));
    }

    @Test
    void makerCannotApproveTheirOwnRequest() {
        Long fdId = maturedDeposit();
        Long requestId = submit(fdId, "maker_a");
        BigDecimal before = balance();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> approvals.decide(requestId, true, null, user("MAKER_A")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("PENDING", requestStatus(requestId));
        assertEquals(0, before.compareTo(balance()));
    }

    @Test
    void rejectionRecordsCheckerAndDoesNotPostFinancialTransaction() {
        Long fdId = maturedDeposit();
        Long requestId = submit(fdId, "maker_a");
        BigDecimal before = balance();
        approvals.decide(requestId, false, "Documentation incomplete", user("checker_b"));
        assertEquals("REJECTED", requestStatus(requestId));
        assertEquals(0, before.compareTo(balance()));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key=?",
                "FD-MATURITY-SETTLEMENT:" + fdId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_fixed_deposit_approval_request
                WHERE request_id=? AND requested_by='maker_a' AND decided_by='checker_b'
                  AND decision_reason='Documentation incomplete' AND decided_at IS NOT NULL
                  AND posting_result IS NULL
                """, requestId));
    }

    @Test
    void duplicatePendingSubmissionAndRepeatedApprovalAreRejected() {
        Long fdId = maturedDeposit();
        Long requestId = submit(fdId, "maker_a");
        ResponseStatusException pending = assertThrows(ResponseStatusException.class,
                () -> submit(fdId, "another_maker"));
        assertEquals(HttpStatus.CONFLICT, pending.getStatusCode());
        approvals.decide(requestId, true, "Approved", user("checker_b"));
        BigDecimal after = balance();
        ResponseStatusException repeated = assertThrows(ResponseStatusException.class,
                () -> approvals.decide(requestId, true, "Try twice", user("checker_c")));
        assertEquals(HttpStatus.CONFLICT, repeated.getStatusCode());
        assertEquals(0, after.compareTo(balance()));
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key=?",
                "FD-MATURITY-SETTLEMENT:" + fdId));
    }

    @Test
    void postingFailureDoesNotAdvanceApprovalOrAlterFinancialRecords() {
        Long fdId = maturedDeposit();
        Long requestId = submit(fdId, "maker_a");
        // Simulate a changed financial state after request submission.
        // Revalidation on approval must reject and keep the request pending.
        jdbc.update("""
                UPDATE public.bank_fixed_deposit_accrual SET accrued_interest=1.00 WHERE fd_id=?
                """, fdId);
        BigDecimal before = balance();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> approvals.decide(requestId, true, "Reviewed", user("checker_b")));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("PENDING", requestStatus(requestId));
        assertEquals(0, before.compareTo(balance()));
        assertEquals("MATURED", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id=?", String.class, fdId));
        assertEquals(0, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key=?",
                "FD-MATURITY-SETTLEMENT:" + fdId));
    }
}
