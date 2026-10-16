package com.cbms.repository;

import com.cbms.entity.BankTransaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, Long> {

    Optional<BankTransaction> findByIdempotencyKey(
            String idempotencyKey
    );

    Page<BankTransaction> findByAcctId(
            Long acctId,
            Pageable pageable
    );

    Page<BankTransaction> findByAcctIdOrDestAcctId(
            Long acctId,
            Long destAcctId,
            Pageable pageable
    );

    boolean existsByReversalOfTxnId(Long reversalOfTxnId);
}
