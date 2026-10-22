package com.cbms.repository;

import com.cbms.entity.TransactionReversalApproval;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface TransactionReversalApprovalRepository extends JpaRepository<TransactionReversalApproval, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TransactionReversalApproval r where r.id = :id")
    Optional<TransactionReversalApproval> lockById(@Param("id") Long id);

    Page<TransactionReversalApproval> findAllByOrderByRequestedAtDesc(Pageable pageable);

    boolean existsByOriginalTxnIdAndStatus(Long txnId, String status);
}
