package com.cbms;

import com.cbms.dto.TransferRequest;
import com.cbms.dto.TransferResponse;
import com.cbms.service.BankTransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/sql/financial-test-fixture.sql", "/sql/financial-transfer-test-fixture.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BankTransferConcurrencyRegressionIntegrationTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    BankTransferService transfers;

    private Long id(String accountNo) {
        return jdbc.queryForObject("SELECT acct_id FROM public.bank_account WHERE acct_no = ?",
                Long.class, accountNo);
    }

    private BigDecimal balance(Long id) {
        return jdbc.queryForObject(
                "SELECT ledger_bal FROM public.bank_account_balance WHERE acct_id = ?",
                BigDecimal.class, id);
    }

    private int count(String key) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?",
                Integer.class, key);
        return value == null ? 0 : value;
    }

    private Authentication maker() {
        return new UsernamePasswordAuthenticationToken("integration-test-maker", null,
                List.of(new SimpleGrantedAuthority("ROLE_MAKER")));
    }

    private void fund(Long account, String amount) throws Exception {
        mockMvc.perform(post("/api/accounts/{acctId}/deposits", account)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_MAKER")))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void insufficientFundsMustRollbackWithoutPartialPosting() throws Exception {
        Long source = id("TEST-LIFE-A");
        Long destination = id("TEST-LIFE-B");
        fund(source, "1.00");
        BigDecimal sourceBefore = balance(source);
        BigDecimal destinationBefore = balance(destination);
        String key = UUID.randomUUID().toString();

        assertThrows(IllegalArgumentException.class, () ->
                transfers.transfer(source,
                        new TransferRequest(destination, new BigDecimal("2.00")),
                        key, maker()));

        assertEquals(0, sourceBefore.compareTo(balance(source)));
        assertEquals(0, destinationBefore.compareTo(balance(destination)));
        assertEquals(0, count(key));
    }

    @Test
    void concurrentSameKeyMustPostExactlyOnce() throws Exception {
        Long source = id("TEST-LIFE-A");
        Long destination = id("TEST-LIFE-B");
        fund(source, "10.00");
        String key = UUID.randomUUID().toString();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<TransferResponse> operation = () -> {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent start");
            }
            return transfers.transfer(source,
                    new TransferRequest(destination, new BigDecimal("0.01")),
                    key, maker());
        };
        try {
            Future<TransferResponse> first = pool.submit(operation);
            Future<TransferResponse> second = pool.submit(operation);
            start.countDown();

            TransferResponse a = first.get(30, TimeUnit.SECONDS);
            TransferResponse b = second.get(30, TimeUnit.SECONDS);
            assertEquals(a.txnId(), b.txnId(), "Idempotent retry must return same transaction");
            assertEquals(1, count(key), "Only one transaction row may be posted");
            assertEquals(0, new BigDecimal("9.99").compareTo(balance(source)));
            assertEquals(0, new BigDecimal("0.01").compareTo(balance(destination)));
        } finally {
            pool.shutdownNow();
        }
    }
}
