
package com.cbms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.jdbc.SqlConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(
        scripts = {
                "/sql/financial-test-fixture.sql",
                "/sql/financial-transfer-test-fixture.sql"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        scripts = "/sql/financial-rollback-trigger.sql",
        config = @SqlConfig(separator = "@@"),
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        scripts = "/sql/financial-rollback-trigger-cleanup.sql",
        config = @SqlConfig(separator = "@@"),
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class BankAtomicRollbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor makerJwt() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt
                        .subject("integration-test-maker")
                        .claim("roles", List.of("MAKER"))
                )
                .authorities(
                        new SimpleGrantedAuthority("ROLE_MAKER")
                );
    }

    private Long accountId(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT acct_id FROM public.bank_account WHERE acct_no = ?",
                Long.class,
                accountNo
        );
    }

    private BigDecimal balance(Long acctId, String column) {
        if (!column.equals("ledger_bal")
                && !column.equals("avail_bal")) {
            throw new IllegalArgumentException("Invalid balance column");
        }

        return jdbcTemplate.queryForObject(
                "SELECT " + column
                        + " FROM public.bank_account_balance WHERE acct_id = ?",
                BigDecimal.class,
                acctId
        );
    }

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                args
        );
        return result == null ? 0 : result;
    }

    @Test
    void ledgerFailureShouldRollbackEntireDeposit() throws Exception {

        Long acctId = accountId("TEST-LIFE-A");
        String idempotencyKey = UUID.randomUUID().toString();

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(balance(acctId, "ledger_bal"))
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(balance(acctId, "avail_bal"))
        );

        int txnBefore = count(
                "SELECT COUNT(*) FROM public.bank_transaction WHERE acct_id = ?",
                acctId
        );

        int entriesBefore = count(
                """
                        SELECT COUNT(*)
                        FROM public.bank_ledger_entry e
                        JOIN public.bank_transaction t
                            ON t.txn_id = e.txn_id
                        WHERE t.acct_id = ?
                        """,
                acctId
        );

        // Attempt deposit. The test trigger deliberately
        // fails when the credit ledger entry is inserted.
        mockMvc.perform(
                        post("/api/accounts/{acctId}/deposits", acctId)
                                .with(makerJwt())
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":500.00}")
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));

        // Both balances must remain unchanged.
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(balance(acctId, "ledger_bal")),
                "Ledger balance changed despite rollback"
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(balance(acctId, "avail_bal")),
                "Available balance changed despite rollback"
        );

        // No transaction should have been committed.
        assertEquals(
                txnBefore,
                count(
                        "SELECT COUNT(*) FROM public.bank_transaction WHERE acct_id = ?",
                        acctId
                ),
                "Failed deposit created a transaction"
        );

        // No partial GL entries should remain.
        assertEquals(
                entriesBefore,
                count(
                        """
                                SELECT COUNT(*)
                                FROM public.bank_ledger_entry e
                                JOIN public.bank_transaction t
                                    ON t.txn_id = e.txn_id
                                WHERE t.acct_id = ?
                                """,
                        acctId
                ),
                "Failed deposit left partial ledger entries"
        );

        // The idempotency key must not have a committed transaction.
        assertEquals(
                0,
                count(
                        """
                                SELECT COUNT(*)
                                FROM public.bank_transaction
                                WHERE idempotency_key = ?
                                """,
                        idempotencyKey
                )
        );
    }
}
