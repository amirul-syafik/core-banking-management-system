package com.cbms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankFinancialLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Long accountId(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT acct_id FROM public.bank_account WHERE acct_no = ?",
                Long.class,
                accountNo
        );
    }

    private BigDecimal balance(Long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT ledger_bal FROM public.bank_account_balance WHERE acct_id = ?",
                BigDecimal.class,
                accountId
        );
    }

    private void assertBalance(Long accountId, String expected) {
        BigDecimal actual = balance(accountId);

        assertNotNull(actual);
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual),
                "Unexpected balance for account " + accountId
        );
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private void assertLedgerBalanced() {
        Integer unbalanced = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM (
                            SELECT
                                t.txn_id
                            FROM public.bank_transaction t
                            LEFT JOIN public.bank_ledger_entry e
                                ON e.txn_id = t.txn_id
                            WHERE t.acct_id IN (
                                SELECT acct_id
                                FROM public.bank_account
                                WHERE acct_no IN ('TEST-LIFE-A', 'TEST-LIFE-B')
                            )
                            GROUP BY t.txn_id
                            HAVING
                                COUNT(e.entry_id) = 0
                                OR COALESCE(SUM(
                                    CASE
                                        WHEN e.entry_side = 'DEBIT'
                                            THEN e.entry_amt
                                        WHEN e.entry_side = 'CREDIT'
                                            THEN -e.entry_amt
                                        ELSE 0
                                    END
                                ), 0) <> 0
                        ) x
                        """,
                Integer.class
        );

        assertEquals(
                0,
                unbalanced,
                "Financial transactions must have balanced GL entries"
        );
    }


    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor makerJwt() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt
                        .subject("integration-test-maker")
                        .claim("roles", java.util.List.of("MAKER"))
                )
                .authorities(
                        new SimpleGrantedAuthority("ROLE_MAKER")
                );
    }

    @Test
    void financialLifecycleShouldMaintainBalancesAndLedger()
            throws Exception {

        Long accountA = accountId("TEST-LIFE-A");
        Long accountB = accountId("TEST-LIFE-B");

        assertBalance(accountA, "0.00");
        assertBalance(accountB, "0.00");

        // 1. DEPOSIT RM500
        String depositKey = UUID.randomUUID().toString();

        mockMvc.perform(
                        post("/api/accounts/{acctId}/deposits", accountA)
                                .with(makerJwt())
                                .header("Idempotency-Key", depositKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("amount", 500.00)))
                )
                .andExpect(status().isCreated());

        assertBalance(accountA, "500.00");

        // 2. WITHDRAW RM150
        mockMvc.perform(
                        post("/api/accounts/{acctId}/withdrawals", accountA)
                                .with(makerJwt())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("amount", 150.00)))
                )
                .andExpect(status().isCreated());

        assertBalance(accountA, "350.00");

        // 3. TRANSFER RM200
        mockMvc.perform(
                        post("/api/accounts/{acctId}/transfers", accountA)
                                .with(makerJwt())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of(
                                        "destinationAccountId", accountB,
                                        "amount", 200.00
                                )))
                )
                .andExpect(status().isCreated());

        assertBalance(accountA, "150.00");
        assertBalance(accountB, "200.00");

        // 4. REPEAT DEPOSIT WITH SAME IDEMPOTENCY KEY
        mockMvc.perform(
                        post("/api/accounts/{acctId}/deposits", accountA)
                                .with(makerJwt())
                                .header("Idempotency-Key", depositKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("amount", 500.00)))
                )
                .andExpect(status().is2xxSuccessful());

        // Balance must not increase again.
        assertBalance(accountA, "150.00");

        // 5. INSUFFICIENT FUNDS WITHDRAWAL
        mockMvc.perform(
                        post("/api/accounts/{acctId}/withdrawals", accountA)
                                .with(makerJwt())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("amount", 1000.00)))
                )
                .andExpect(status().is4xxClientError());

        assertBalance(accountA, "150.00");
        assertBalance(accountB, "200.00");

        // 6. VERIFY DOUBLE-ENTRY LEDGER
        assertLedgerBalanced();
    }
}
