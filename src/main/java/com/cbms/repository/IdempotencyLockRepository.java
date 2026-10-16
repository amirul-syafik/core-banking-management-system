package com.cbms.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyLockRepository {

    private final EntityManager entityManager;

    public IdempotencyLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void lock(String idempotencyKey) {
        entityManager.createNativeQuery(
                        """
                        SELECT pg_advisory_xact_lock(
                            hashtextextended(:key, 0)
                        )
                        """
                )
                .setParameter("key", idempotencyKey)
                .getSingleResult();
    }


    public void lockReversal(Long originalTxnId) {
        entityManager.createNativeQuery(
                        """
                        SELECT pg_advisory_xact_lock(
                            hashtextextended(:key, 0)
                        )
                        """
                )
                .setParameter(
                        "key",
                        "CBMS:REVERSAL:" + originalTxnId
                )
                .getSingleResult();
    }
}
