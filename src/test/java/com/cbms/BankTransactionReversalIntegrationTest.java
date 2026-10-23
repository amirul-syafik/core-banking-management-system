
package com.cbms;

import com.cbms.dto.TransactionReversalRequest;
import com.cbms.dto.TransactionReversalResponse;
import com.cbms.entity.BankTransaction;
import com.cbms.repository.BankTransactionRepository;
import com.cbms.service.BankTransactionReversalService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BankTransactionReversalIntegrationTest {

    @Autowired
    private BankTransactionReversalService reversalService;

    @Autowired
    private BankTransactionRepository transactionRepository;

    private Authentication adminAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "integration-test-admin",
                null,
                List.of()
        );
    }

    @Test
    void concurrentReversalShouldOnlyCreateOneReversal()
            throws Exception {

        BankTransaction original = transactionRepository
                .findAll()
                .stream()
                .filter(t -> "TEST-DEP-900001".equals(t.getTxnRef()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Test deposit fixture not found"
                        )
                );

        Long originalTxnId = original.getTxnId();

        assertEquals("DEPOSIT", original.getTxnTyp());
        assertEquals("POSTED", original.getTxnSts());

        assertFalse(
                transactionRepository
                        .existsByReversalOfTxnId(originalTxnId)
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> attempt = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for test start"
                );
            }

            try {
                TransactionReversalResponse response =
                        reversalService.reverse(
                                originalTxnId,
                                new TransactionReversalRequest(
                                        "Concurrent reversal test"
                                ),
                                UUID.randomUUID().toString(),
                                adminAuthentication()
                        );

                return response.reversalTxnId() != null;

            } catch (IllegalArgumentException ex) {
                return false;
            }
        };

        try {
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS)
            );

            start.countDown();

            boolean firstSucceeded =
                    first.get(30, TimeUnit.SECONDS);

            boolean secondSucceeded =
                    second.get(30, TimeUnit.SECONDS);

            assertEquals(
                    1,
                    (firstSucceeded ? 1 : 0)
                            + (secondSucceeded ? 1 : 0),
                    "Exactly one reversal must succeed"
            );

            long reversalCount = transactionRepository
                    .findAll()
                    .stream()
                    .filter(t -> originalTxnId.equals(
                            t.getReversalOfTxnId()
                    ))
                    .count();

            assertEquals(
                    1,
                    reversalCount,
                    "Only one reversal transaction must exist"
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
