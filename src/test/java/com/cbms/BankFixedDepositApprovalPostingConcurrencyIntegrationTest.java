package com.cbms;

import com.cbms.service.BankFixedDepositApprovalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uses two real, independently committed Spring transactions against PostgreSQL.
 * No @Transactional on the test class: approving on a separate connection must
 * see a committed fixture and a committed pending request.
 *
 * Run only against the isolated corebank_lite_test database.
 */
@SpringBootTest
@ActiveProfiles("test")
class BankFixedDepositApprovalPostingConcurrencyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BankFixedDepositApprovalService approvals;

    private static final BigDecimal PRINCIPAL = new BigDecimal("40.00");
    private Long customerId;
    private Long productId;
    private Long schemeId;
    private Long accountId;
    private Long fdId;
    private Long requestId;

    private Authentication actor(String name) {
        return new UsernamePasswordAuthenticationToken(name, "unused", List.of());
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private BigDecimal balance() {
        return jdbc.queryForObject(
                "SELECT ledger_bal FROM public.bank_account_balance WHERE acct_id=?",
                BigDecimal.class, accountId);
    }

    @BeforeEach
    void setup() {
        // Ensure an accidental wrong database selection fails closed, before inserting data.
        assertEquals("corebank_lite_test", jdbc.queryForObject("SELECT current_database()", String.class));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        jdbc.update("""
            INSERT INTO public.bank_ledger_account (gl_cd,gl_nm,gl_typ,ccy_cd,act_flg)
            VALUES ('200100','Customer deposits','LIABILITY','MYR',TRUE),
                   ('200200','FD liability','LIABILITY','MYR',TRUE)
            ON CONFLICT (gl_cd) DO NOTHING
            """);
        customerId = jdbc.queryForObject("""
            INSERT INTO public.customer_master (cust_nm,cust_typ,sts,crt_by,cif_no)
            VALUES ('FD approval posting race','INDIVIDUAL','ACTIVE','FD_APPROVAL_RACE',?)
            RETURNING cust_id
            """, Long.class, "FR" + suffix);
        accountId = jdbc.queryForObject("""
            INSERT INTO public.bank_account (cust_id,acct_no,acct_typ,ccy_cd,acct_sts,crt_by)
            VALUES (?,?,'SAVINGS','MYR','ACTIVE','FD_APPROVAL_RACE')
            RETURNING acct_id
            """, Long.class, customerId, "FD-RACE-" + suffix);
        jdbc.update("""
            INSERT INTO public.bank_account_balance (acct_id,ledger_bal,avail_bal,upd_by)
            VALUES (?,60.00,60.00,'FD_APPROVAL_RACE')
            """, accountId);
        productId = jdbc.queryForObject("""
            INSERT INTO public.bank_product (prod_cd,prod_nm,prod_cat,banking_typ,prod_sts,crt_by)
            VALUES (?,'FD race product','FIXED_DEPOSIT','CONVENTIONAL','ACTIVE','FD_APPROVAL_RACE')
            RETURNING prod_id
            """, Long.class, "FR" + suffix);
        schemeId = jdbc.queryForObject("""
            INSERT INTO public.bank_scheme (prod_id,schm_cd,schm_nm,schm_sts,crt_by)
            VALUES (?,?,'FD race scheme','ACTIVE','FD_APPROVAL_RACE')
            RETURNING schm_id
            """, Long.class, productId, "FR" + suffix);
        fdId = jdbc.queryForObject("""
            INSERT INTO public.bank_fixed_deposit
                (fd_ref,cust_id,prod_id,schm_id,fd_typ,principal_amt,tenor_mth,
                 placement_dt,maturity_dt,interest_rate,fd_sts,crt_by)
            VALUES (?,?,?,?,'FIXED_DEPOSIT',?,1,?,?,3.65,'MATURED','FD_APPROVAL_RACE')
            RETURNING fd_id
            """, Long.class, "FD-RACE-" + suffix, customerId, productId, schemeId,
                PRINCIPAL, LocalDate.now().minusMonths(1), LocalDate.now());
        jdbc.update("""
            INSERT INTO public.bank_fixed_deposit_maturity
                (fd_id,maturity_ref,maturity_dt,principal_amt,maturity_sts,crt_by)
            VALUES (?,?,? ,?,'PROCESSED','FD_APPROVAL_RACE')
            """, fdId, "MAT-RACE-" + suffix, LocalDate.now(), PRINCIPAL);
        Map<String,Object> submitted = approvals.submit(fdId, "MATURITY_SETTLEMENT",
                Map.of("destinationAccountId", accountId, "remarks", "Race settlement"),
                "Concurrency regression", actor("race_maker"));
        requestId = ((Number) submitted.get("requestId")).longValue();
    }

    @AfterEach
    void cleanup() {
        // Delete generated financial postings before FK parents. Each fixture has unique IDs.
        if (fdId != null) {
            String key = "FD-MATURITY-SETTLEMENT:" + fdId;
            jdbc.update("""
                DELETE FROM public.bank_ledger_entry WHERE txn_id IN
                (SELECT txn_id FROM public.bank_transaction WHERE idempotency_key=?)
                """, key);
            jdbc.update("DELETE FROM public.bank_transaction WHERE idempotency_key=?", key);
            jdbc.update("DELETE FROM public.bank_fixed_deposit_approval_request WHERE fd_id=?", fdId);
            jdbc.update("DELETE FROM public.bank_fixed_deposit_maturity WHERE fd_id=?", fdId);
            jdbc.update("DELETE FROM public.bank_fixed_deposit WHERE fd_id=?", fdId);
        }
        if (accountId != null) {
            jdbc.update("DELETE FROM public.bank_account_balance WHERE acct_id=?", accountId);
            jdbc.update("DELETE FROM public.bank_account WHERE acct_id=?", accountId);
        }
        if (schemeId != null) jdbc.update("DELETE FROM public.bank_scheme WHERE schm_id=?", schemeId);
        if (productId != null) jdbc.update("DELETE FROM public.bank_product WHERE prod_id=?", productId);
        if (customerId != null) jdbc.update("DELETE FROM public.customer_master WHERE cust_id=?", customerId);
    }

    private Object approve(String checker) {
        try {
            return approvals.decide(requestId, true, "Approved during contention", actor(checker));
        } catch (ResponseStatusException e) {
            return e.getStatusCode();
        }
    }

    @Test
    void twoSimultaneousCheckersPostMaturitySettlementOnlyOnce() throws Exception {
        BigDecimal before = balance();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Object> first = () -> { start.await(); return approve("race_checker_a"); };
            Callable<Object> second = () -> { start.await(); return approve("race_checker_b"); };
            Future<Object> a = pool.submit(first);
            Future<Object> b = pool.submit(second);
            start.countDown();
            List<Object> results = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(Map.class::isInstance).count(), results.toString());
            assertEquals(1, results.stream().filter(HttpStatus.CONFLICT::equals).count(), results.toString());
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, balance().subtract(before).compareTo(PRINCIPAL),
                "Destination account must receive principal exactly once");
        assertEquals("CLOSED", jdbc.queryForObject(
                "SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id=?", String.class, fdId));
        String key = "FD-MATURITY-SETTLEMENT:" + fdId;
        assertEquals(1, count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key=?", key));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_transaction t
                WHERE t.idempotency_key=? AND t.txn_sts='POSTED' AND t.txn_amt=?
                  AND (SELECT COUNT(*) FROM public.bank_ledger_entry e WHERE e.txn_id=t.txn_id)=2
                  AND (SELECT COALESCE(SUM(CASE WHEN e.entry_side='DEBIT' THEN e.entry_amt
                                                 ELSE -e.entry_amt END),0)
                       FROM public.bank_ledger_entry e WHERE e.txn_id=t.txn_id)=0
                """, key, PRINCIPAL));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM public.bank_fixed_deposit_approval_request
                WHERE request_id=? AND request_status='APPROVED'
                  AND requested_by='race_maker'
                  AND decided_by IN ('race_checker_a','race_checker_b')
                  AND decided_at IS NOT NULL AND posting_result IS NOT NULL
                """, requestId));
    }
}
