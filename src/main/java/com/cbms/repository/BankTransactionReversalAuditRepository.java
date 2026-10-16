package com.cbms.repository;

import com.cbms.entity.BankTransactionReversalAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankTransactionReversalAuditRepository
        extends JpaRepository<BankTransactionReversalAudit, Long> {

    Optional<BankTransactionReversalAudit> findByReversalTxnId(
            Long reversalTxnId
    );
}
