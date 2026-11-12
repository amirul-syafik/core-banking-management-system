package com.cbms;

import com.cbms.dto.BankPostingResponse;
import com.cbms.dto.TransferRequest;
import com.cbms.dto.TransferResponse;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = {"/sql/financial-test-fixture.sql", "/sql/financial-transfer-test-fixture.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BankIdempotencyAndConcurrencyIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired BankPostingService postings;
    @Autowired BankTransferService transfers;

    private Authentication maker() {
        return new UsernamePasswordAuthenticationToken("idempotency-test-maker", null,
                List.of(new SimpleGrantedAuthority("ROLE_MAKER")));
    }

    private Long id(String number) {
        return jdbc.queryForObject("SELECT acct_id FROM public.bank_account WHERE acct_no = ?", Long.class, number);
    }

    private BigDecimal balance(Long acctId) {
        return jdbc.queryForObject("SELECT ledger_bal FROM public.bank_account_balance WHERE acct_id = ?",
                BigDecimal.class, acctId);
    }

    private int count(String key) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?",
                Integer.class, key);
        return n == null ? 0 : n;
    }

    private int ledgerCount(Long txnId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM public.bank_ledger_entry WHERE txn_id = ?",
                Integer.class, txnId);
        return n == null ? 0 : n;
    }

    @Test
    void postingSameKeySamePayloadReturnsOriginalTransactionWithoutDoublePosting() {
        Long acct = id("TEST-LIFE-A");
        String key = UUID.randomUUID().toString();
        BankPostingResponse first = postings.post(acct, "DEPOSIT", new BigDecimal("10.00"), key, maker());
        BankPostingResponse second = postings.post(acct, "DEPOSIT", new BigDecimal("10.0"), key, maker());
        assertEquals(first.txnId(), second.txnId());
        assertEquals(1, count(key));
        assertEquals(2, ledgerCount(first.txnId()));
        assertEquals(0, new BigDecimal("10.00").compareTo(balance(acct)));
    }

    @Test
    void postingReusedKeyWithDifferentAmountAccountOrTypeMustBeRejected() {
        Long a = id("TEST-LIFE-A");
        Long b = id("TEST-LIFE-B");
        String key = UUID.randomUUID().toString();
        postings.post(a, "DEPOSIT", new BigDecimal("10.00"), key, maker());
        assertThrows(IllegalArgumentException.class,
                () -> postings.post(a, "DEPOSIT", new BigDecimal("11.00"), key, maker()));
        assertThrows(IllegalArgumentException.class,
                () -> postings.post(b, "DEPOSIT", new BigDecimal("10.00"), key, maker()));
        assertThrows(IllegalArgumentException.class,
                () -> postings.post(a, "WITHDRAWAL", new BigDecimal("10.00"), key, maker()));
        assertEquals(1, count(key));
        assertEquals(0, new BigDecimal("10.00").compareTo(balance(a)));
        assertEquals(0, BigDecimal.ZERO.compareTo(balance(b)));
    }

    @Test
    void transferReusedKeyWithDifferentDestinationOrAmountMustBeRejected() {
        Long a = id("TEST-LIFE-A");
        Long b = id("TEST-LIFE-B");
        postings.post(a, "DEPOSIT", new BigDecimal("20.00"), UUID.randomUUID().toString(), maker());
        String key = UUID.randomUUID().toString();
        TransferResponse first = transfers.transfer(a,
                new TransferRequest(b, new BigDecimal("5.00")), key, maker());
        TransferResponse retry = transfers.transfer(a,
                new TransferRequest(b, new BigDecimal("5.0")), key, maker());
        assertEquals(first.txnId(), retry.txnId());
        assertThrows(IllegalArgumentException.class,
                () -> transfers.transfer(a, new TransferRequest(b, new BigDecimal("6.00")), key, maker()));
        assertThrows(IllegalArgumentException.class,
                () -> transfers.transfer(b, new TransferRequest(a, new BigDecimal("5.00")), key, maker()));
        assertEquals(1, count(key));
        assertEquals(2, ledgerCount(first.txnId()));
        assertEquals(0, new BigDecimal("15.00").compareTo(balance(a)));
        assertEquals(0, new BigDecimal("5.00").compareTo(balance(b)));
    }

    @Test
    void concurrentWithdrawalsUsingDifferentKeysCannotOverdrawAccount() throws Exception {
        Long acct = id("TEST-LIFE-A");
        postings.post(acct, "DEPOSIT", new BigDecimal("10.00"), UUID.randomUUID().toString(), maker());
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            Callable<Object> one = () -> attemptWithdrawal(acct, key1, gate);
            Callable<Object> two = () -> attemptWithdrawal(acct, key2, gate);
            Future<Object> f1 = pool.submit(one);
            Future<Object> f2 = pool.submit(two);
            gate.countDown();
            Object r1 = f1.get(30, TimeUnit.SECONDS);
            Object r2 = f2.get(30, TimeUnit.SECONDS);
            long successful = List.of(r1, r2).stream().filter(BankPostingResponse.class::isInstance).count();
            long insufficient = List.of(r1, r2).stream().filter(IllegalArgumentException.class::isInstance).count();
            assertEquals(1, successful, "Exactly one withdrawal must succeed");
            assertEquals(1, insufficient, "Other withdrawal must be rejected");
            assertEquals(1, count(key1) + count(key2));
            assertEquals(0, new BigDecimal("3.00").compareTo(balance(acct)));
        } finally {
            pool.shutdownNow();
        }
    }

    private Object attemptWithdrawal(Long acct, String key, CountDownLatch gate) throws Exception {
        if (!gate.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent start timed out");
        try {
            return postings.post(acct, "WITHDRAWAL", new BigDecimal("7.00"), key, maker());
        } catch (IllegalArgumentException ex) {
            if (!"Insufficient funds".equals(ex.getMessage())) throw ex;
            return ex;
        }
    }

    @Test
    void simultaneousOppositeDirectionTransfersPreserveTotalMoney() throws Exception {
        Long a = id("TEST-LIFE-A");
        Long b = id("TEST-LIFE-B");
        postings.post(a, "DEPOSIT", new BigDecimal("20.00"), UUID.randomUUID().toString(), maker());
        postings.post(b, "DEPOSIT", new BigDecimal("20.00"), UUID.randomUUID().toString(), maker());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            Callable<TransferResponse> forward = () -> {
                if (!gate.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent start timed out");
                return transfers.transfer(a, new TransferRequest(b, new BigDecimal("7.00")),
                        UUID.randomUUID().toString(), maker());
            };
            Callable<TransferResponse> backward = () -> {
                if (!gate.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent start timed out");
                return transfers.transfer(b, new TransferRequest(a, new BigDecimal("4.00")),
                        UUID.randomUUID().toString(), maker());
            };
            Future<TransferResponse> f1 = pool.submit(forward);
            Future<TransferResponse> f2 = pool.submit(backward);
            gate.countDown();
            TransferResponse t1 = f1.get(30, TimeUnit.SECONDS);
            TransferResponse t2 = f2.get(30, TimeUnit.SECONDS);
            assertNotEquals(t1.txnId(), t2.txnId());
            assertEquals(2, ledgerCount(t1.txnId()));
            assertEquals(2, ledgerCount(t2.txnId()));
            assertEquals(0, new BigDecimal("17.00").compareTo(balance(a)));
            assertEquals(0, new BigDecimal("23.00").compareTo(balance(b)));
            assertEquals(0, new BigDecimal("40.00").compareTo(balance(a).add(balance(b))));
        } finally {
            pool.shutdownNow();
        }
    }
}
