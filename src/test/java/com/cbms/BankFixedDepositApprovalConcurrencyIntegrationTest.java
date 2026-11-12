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
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL row-lock regression tests. Intentionally NOT @Transactional:
 * two competing calls must use separate committed database transactions.
 * Fixture rows are unique per test and removed in @AfterEach.
 */
@SpringBootTest
@ActiveProfiles("test")
class BankFixedDepositApprovalConcurrencyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BankFixedDepositApprovalService approvals;

    private Long customerId;
    private Long productId;
    private Long schemeId;
    private Long fdId;

    private Authentication actor(String username) {
        return new UsernamePasswordAuthenticationToken(username, "unused", List.of());
    }

    @BeforeEach
    void createIsolatedFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        customerId = jdbc.queryForObject("""
            INSERT INTO public.customer_master (cust_nm,cust_typ,sts,crt_by,cif_no)
            VALUES ('FD concurrent approval test','INDIVIDUAL','ACTIVE','FD_CONCURRENCY_TEST',?)
            RETURNING cust_id
            """, Long.class, "FC" + suffix);
        productId = jdbc.queryForObject("""
            INSERT INTO public.bank_product (prod_cd,prod_nm,prod_cat,banking_typ,prod_sts,crt_by)
            VALUES (?,'FD concurrency product','FIXED_DEPOSIT','CONVENTIONAL','ACTIVE','FD_CONCURRENCY_TEST')
            RETURNING prod_id
            """, Long.class, "FC" + suffix);
        schemeId = jdbc.queryForObject("""
            INSERT INTO public.bank_scheme (prod_id,schm_cd,schm_nm,schm_sts,crt_by)
            VALUES (?,?,'FD concurrency scheme','ACTIVE','FD_CONCURRENCY_TEST')
            RETURNING schm_id
            """, Long.class, productId, "FC" + suffix);
        fdId = jdbc.queryForObject("""
            INSERT INTO public.bank_fixed_deposit
                (fd_ref,cust_id,prod_id,schm_id,fd_typ,principal_amt,tenor_mth,
                 placement_dt,maturity_dt,interest_rate,fd_sts,crt_by)
            VALUES (?,?,?,?,'FIXED_DEPOSIT',100.00,1,?,?,3.65,'MATURED','FD_CONCURRENCY_TEST')
            RETURNING fd_id
            """, Long.class, "FD-FC-" + suffix, customerId, productId, schemeId,
                LocalDate.now().minusMonths(1), LocalDate.now());
    }

    @AfterEach
    void removeIsolatedFixture() {
        // These tests only submit and reject. They must never create financial postings.
        if (fdId != null) {
            jdbc.update("DELETE FROM public.bank_fixed_deposit_approval_request WHERE fd_id=?", fdId);
            jdbc.update("DELETE FROM public.bank_fixed_deposit WHERE fd_id=?", fdId);
        }
        if (schemeId != null) jdbc.update("DELETE FROM public.bank_scheme WHERE schm_id=?", schemeId);
        if (productId != null) jdbc.update("DELETE FROM public.bank_product WHERE prod_id=?", productId);
        if (customerId != null) jdbc.update("DELETE FROM public.customer_master WHERE cust_id=?", customerId);
    }

    private Object submit(String maker) {
        try {
            return approvals.submit(fdId, "MATURITY_SETTLEMENT",
                Map.of("destinationAccountId", 1L, "remarks", "Concurrent request"),
                "Concurrency regression", actor(maker));
        } catch (ResponseStatusException e) {
            return e.getStatusCode();
        }
    }

    private Object reject(Long requestId, String checker) {
        try {
            return approvals.decide(requestId, false, "Concurrent review", actor(checker));
        } catch (ResponseStatusException e) {
            return e.getStatusCode();
        }
    }

    private List<Object> race(Callable<Object> left, Callable<Object> right) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> a = pool.submit(() -> { start.await(); return left.call(); });
            Future<Object> b = pool.submit(() -> { start.await(); return right.call(); });
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            fail("Concurrent FD approval operation blocked for more than 20 seconds", e);
            throw e;
        } finally {
            pool.shutdownNow();
        }
    }

    private int count(String sql, Object... params) {
        return jdbc.queryForObject(sql, Integer.class, params);
    }

    @Test
    void twoMakersCannotCreateTwoPendingRequestsForSameFd() throws Exception {
        List<Object> outcome = race(() -> submit("maker_alpha"), () -> submit("maker_beta"));
        assertEquals(1, outcome.stream().filter(Map.class::isInstance).count(), outcome.toString());
        assertEquals(1, outcome.stream().filter(HttpStatus.CONFLICT::equals).count(), outcome.toString());
        assertEquals(1, count("""
            SELECT COUNT(*) FROM public.bank_fixed_deposit_approval_request
            WHERE fd_id=? AND request_status='PENDING'
            """, fdId));
    }

    @Test
    void twoCheckersCannotBothDecideSamePendingRequest() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> submitted = (Map<String, Object>) submit("maker_alpha");
        Long requestId = ((Number) submitted.get("requestId")).longValue();
        List<Object> outcome = race(() -> reject(requestId, "checker_alpha"),
                                    () -> reject(requestId, "checker_beta"));
        assertEquals(1, outcome.stream().filter(Map.class::isInstance).count(), outcome.toString());
        assertEquals(1, outcome.stream().filter(HttpStatus.CONFLICT::equals).count(), outcome.toString());
        assertEquals(1, count("""
            SELECT COUNT(*) FROM public.bank_fixed_deposit_approval_request
            WHERE request_id=? AND request_status='REJECTED'
              AND decided_by IN ('checker_alpha','checker_beta')
              AND decided_at IS NOT NULL AND decision_reason='Concurrent review'
            """, requestId));
    }
}
