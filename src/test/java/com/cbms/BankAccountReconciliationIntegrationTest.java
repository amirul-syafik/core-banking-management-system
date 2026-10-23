package com.cbms;

import com.cbms.dto.TransactionReversalRequest;
import com.cbms.entity.BankTransaction;
import com.cbms.repository.BankTransactionRepository;
import com.cbms.service.BankTransactionReversalService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BankAccountReconciliationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BankTransactionRepository transactionRepository;

    @Autowired
    private BankTransactionReversalService reversalService;

    private Authentication adminAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "integration-test-admin",
                null,
                List.of()
        );
    }

    private Long getTestAccountId() {
        return jdbcTemplate.queryForObject(
                "SELECT acct_id FROM public.bank_account " +
                        "WHERE acct_no = 'TEST-REV-900001'",
                Long.class
        );
    }

    private BigDecimal getLedgerBalance(Long acctId) {
        return jdbcTemplate.queryForObject(
                "SELECT ledger_bal FROM public.bank_account_balance " +
                        "WHERE acct_id = ?",
                BigDecimal.class,
                acctId
        );
    }

    private BigDecimal getNetCustomerDepositGl(Long acctId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(SUM(
                            CASE
                                WHEN e.entry_side = 'CREDIT' THEN e.entry_amt
                                WHEN e.entry_side = 'DEBIT' THEN -e.entry_amt
                                ELSE 0
                            END
                        ), 0)
                        FROM public.bank_ledger_entry e
                        JOIN public.bank_ledger_account g
                            ON g.gl_id = e.gl_id
                        JOIN public.bank_transaction t
                            ON t.txn_id = e.txn_id
                        WHERE e.acct_id = ?
                          AND g.gl_cd = '200100'
                          AND t.txn_sts = 'POSTED'
                        """,
                BigDecimal.class,
                acctId
        );
    }

    private void assertReconciled(Long acctId, String stage) {

        BigDecimal storedBalance = getLedgerBalance(acctId);
        BigDecimal glBalance = getNetCustomerDepositGl(acctId);

        assertNotNull(storedBalance);
        assertNotNull(glBalance);

        assertEquals(
                0,
                storedBalance.compareTo(glBalance),
                "Reconciliation mismatch at: " + stage
        );
    }

    @Test
    void accountShouldReconcileBeforeAndAfterDepositReversal() {

        Long acctId = getTestAccountId();

        BankTransaction deposit = transactionRepository.findAll()
                .stream()
                .filter(t -> "TEST-DEP-900001".equals(t.getTxnRef()))
                .findFirst()
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("100.00")
                        .compareTo(getLedgerBalance(acctId))
        );

        assertFalse(
                transactionRepository.existsByReversalOfTxnId(
                        deposit.getTxnId()
                )
        );

        // Before reversal: RM100 stored balance and RM100 GL.
        assertReconciled(acctId, "Before reversal");

        // Reverse the original deposit.
        reversalService.reverse(
                deposit.getTxnId(),
                new TransactionReversalRequest(
                        "Reconciliation integration test"
                ),
                UUID.randomUUID().toString(),
                adminAuthentication()
        );

        // After reversal: both should return to RM0.
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(getLedgerBalance(acctId))
        );

        assertReconciled(acctId, "After reversal");

        assertTrue(
                transactionRepository.existsByReversalOfTxnId(
                        deposit.getTxnId()
                )
        );
    }

    @Test
    void everyFixtureTransactionShouldHaveBalancedLedgerEntries() {

        Long acctId = getTestAccountId();

        Integer unbalancedTransactions = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM (
                            SELECT
                                t.txn_id,
                                COALESCE(SUM(
                                    CASE
                                        WHEN e.entry_side = 'DEBIT'
                                            THEN e.entry_amt
                                        ELSE -e.entry_amt
                                    END
                                ), 0) AS difference
                            FROM public.bank_transaction t
                            LEFT JOIN public.bank_ledger_entry e
                                ON e.txn_id = t.txn_id
                            WHERE t.acct_id = ?
                            GROUP BY t.txn_id
                            HAVING COALESCE(SUM(
                                CASE
                                    WHEN e.entry_side = 'DEBIT'
                                        THEN e.entry_amt
                                    ELSE -e.entry_amt
                                END
                            ), 0) <> 0
                               OR COUNT(e.entry_id) = 0
                        ) unbalanced
                        """,
                Integer.class,
                acctId
        );

        assertEquals(
                0,
                unbalancedTransactions,
                "All transactions must have balanced ledger entries"
        );
    }
}
