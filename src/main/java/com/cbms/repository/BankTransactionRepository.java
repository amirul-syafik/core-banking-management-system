package com.cbms.repository;

import com.cbms.entity.BankTransaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

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

    @Query("SELECT t FROM BankTransaction t WHERE (t.acctId = :accountId OR t.destAcctId = :accountId) " +
            "AND (:reference = '' OR LOWER(t.txnRef) LIKE LOWER(CONCAT('%', :reference, '%'))) " +
            "AND (:type = '' OR t.txnTyp = :type) " +
            "AND (:status = '' OR t.txnSts = :status) " +
            "AND (t.crtDt >= :fromDate) AND (t.crtDt < :toDate)")
    Page<BankTransaction> inquireByAccount(@Param("accountId") Long accountId,
                                           @Param("reference") String reference, @Param("type") String type,
                                           @Param("status") String status, @Param("fromDate") OffsetDateTime fromDate,
                                           @Param("toDate") OffsetDateTime toDate, Pageable pageable);

    boolean existsByReversalOfTxnId(Long reversalOfTxnId);
}
