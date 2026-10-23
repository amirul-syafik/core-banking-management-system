package com.cbms;

import com.cbms.dto.TransactionReversalRequest;
import com.cbms.dto.TransactionReversalResponse;
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
class BankTransactionRollbackIntegrationTest {

    @Autowired
    private BankTransactionReversalService reversalService;

    @Autowired
    private BankTransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Authentication adminAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "integration-test-admin",
                null,
                List.of()
        );
    }

    private Integer count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                args
        );
    }

    private BigDecimal getBalance(Long acctId) {
        return jdbcTemplate.queryForObject(
                "SELECT ledger_bal FROM public.bank_account_balance " +
                        "WHERE acct_id = ?",
                BigDecimal.class,
                acctId
        );
    }

    @Test
    void secondReversalShouldFailWithoutChangingFinancialRecords() {

        BankTransaction original = transactionRepository.findAll()
                .stream()
                .filter(t -> "TEST-DEP-900001".equals(t.getTxnRef()))
                .findFirst()
                .orElseThrow();

        Long originalTxnId = original.getTxnId();
        Long acctId = original.getAcctId();

        assertFalse(
                transactionRepository.existsByReversalOfTxnId(originalTxnId)
        );

        // First reversal succeeds.
        TransactionReversalResponse first = reversalService.reverse(
                originalTxnId,
                new TransactionReversalRequest("First valid reversal"),
                UUID.randomUUID().toString(),
                adminAuthentication()
        );

        assertNotNull(first.reversalTxnId());

        BigDecimal balanceBefore = getBalance(acctId);

        Integer txnCountBefore = count(
                "SELECT COUNT(*) FROM public.bank_transaction " +
                        "WHERE acct_id = ?",
                acctId
        );

        Integer entryCountBefore = count(
                "SELECT COUNT(*) FROM public.bank_ledger_entry " +
                        "WHERE txn_id IN (" +
                        "SELECT txn_id FROM public.bank_transaction " +
                        "WHERE acct_id = ?)",
                acctId
        );

        Integer auditCountBefore = count(
                "SELECT COUNT(*) " +
                        "FROM public.bank_transaction_reversal_audit " +
                        "WHERE original_txn_id = ?",
                originalTxnId
        );

        // Attempt a second reversal using a different idempotency key.
        assertThrows(
                IllegalArgumentException.class,
                () -> reversalService.reverse(
                        originalTxnId,
                        new TransactionReversalRequest(
                                "Second reversal must fail"
                        ),
                        UUID.randomUUID().toString(),
                        adminAuthentication()
                )
        );

        BigDecimal balanceAfter = getBalance(acctId);

        Integer txnCountAfter = count(
                "SELECT COUNT(*) FROM public.bank_transaction " +
                        "WHERE acct_id = ?",
                acctId
        );

        Integer entryCountAfter = count(
                "SELECT COUNT(*) FROM public.bank_ledger_entry " +
                        "WHERE txn_id IN (" +
                        "SELECT txn_id FROM public.bank_transaction " +
                        "WHERE acct_id = ?)",
                acctId
        );

        Integer auditCountAfter = count(
                "SELECT COUNT(*) " +
                        "FROM public.bank_transaction_reversal_audit " +
                        "WHERE original_txn_id = ?",
                originalTxnId
        );

        assertEquals(
                0,
                balanceBefore.compareTo(balanceAfter),
                "Rejected reversal must not change account balance"
        );

        assertEquals(
                txnCountBefore,
                txnCountAfter,
                "Rejected reversal must not create another transaction"
        );

        assertEquals(
                entryCountBefore,
                entryCountAfter,
                "Rejected reversal must not create ledger entries"
        );

        assertEquals(
                auditCountBefore,
                auditCountAfter,
                "Rejected reversal must not create an audit record"
        );

        assertEquals(
                1,
                auditCountAfter,
                "Exactly one successful reversal audit should exist"
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(balanceAfter),
                "Account balance should remain zero"
        );
    }
}
