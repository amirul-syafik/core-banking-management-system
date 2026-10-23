
package com.cbms;

import com.cbms.dto.TransactionReversalRequest;
import com.cbms.entity.BankTransaction;
import com.cbms.repository.BankTransactionRepository;
import com.cbms.service.BankTransactionReversalService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BankTransactionInsufficientFundsIntegrationTest {

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

    @Test
    void depositReversalShouldFailWhenFundsAreInsufficient() {

        BankTransaction original = transactionRepository
                .findAll()
                .stream()
                .filter(t -> "TEST-DEP-900001".equals(t.getTxnRef()))
                .findFirst()
                .orElseThrow();

        Long txnId = original.getTxnId();
        Long acctId = original.getAcctId();

        BigDecimal balanceBefore = jdbcTemplate.queryForObject(
                "SELECT avail_bal FROM public.bank_account_balance " +
                        "WHERE acct_id = ?",
                BigDecimal.class,
                acctId
        );

        assertNotNull(balanceBefore);
        assertEquals(
                0,
                new BigDecimal("20.00").compareTo(balanceBefore)
        );

        Integer transactionCountBefore =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM public.bank_transaction " +
                                "WHERE acct_id = ?",
                        Integer.class,
                        acctId
                );

        Integer ledgerCountBefore =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM public.bank_ledger_entry " +
                                "WHERE txn_id IN (" +
                                "SELECT txn_id FROM public.bank_transaction " +
                                "WHERE acct_id = ?)",
                        Integer.class,
                        acctId
                );

        assertFalse(
                transactionRepository.existsByReversalOfTxnId(txnId),
                "Test fixture must start with an unreversed deposit"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reversalService.reverse(
                        txnId,
                        new TransactionReversalRequest(
                                "Insufficient funds reversal test"
                        ),
                        UUID.randomUUID().toString(),
                        adminAuthentication()
                )
        );

        assertTrue(
                exception.getMessage().toLowerCase().contains("insufficient"),
                "Expected insufficient funds error, but received: "
                        + exception.getMessage()
        );

        BigDecimal balanceAfter = jdbcTemplate.queryForObject(
                "SELECT avail_bal FROM public.bank_account_balance " +
                        "WHERE acct_id = ?",
                BigDecimal.class,
                acctId
        );

        assertNotNull(balanceAfter);
        assertEquals(0, balanceBefore.compareTo(balanceAfter));

        Integer transactionCountAfter =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM public.bank_transaction " +
                                "WHERE acct_id = ?",
                        Integer.class,
                        acctId
                );

        Integer ledgerCountAfter =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM public.bank_ledger_entry " +
                                "WHERE txn_id IN (" +
                                "SELECT txn_id FROM public.bank_transaction " +
                                "WHERE acct_id = ?)",
                        Integer.class,
                        acctId
                );

        assertEquals(
                transactionCountBefore,
                transactionCountAfter,
                "Failed reversal must not create a transaction"
        );

        assertEquals(
                ledgerCountBefore,
                ledgerCountAfter,
                "Failed reversal must not create ledger entries"
        );

        assertFalse(
                transactionRepository.existsByReversalOfTxnId(txnId),
                "Original deposit must remain unreversed"
        );
    }
}
