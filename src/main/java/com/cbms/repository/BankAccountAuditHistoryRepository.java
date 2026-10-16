package com.cbms.repository;

import com.cbms.entity.BankAccountAuditHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountAuditHistoryRepository
        extends JpaRepository<BankAccountAuditHistory, Long> {

    Page<BankAccountAuditHistory> findByAcctId(
            Long acctId,
            Pageable pageable
    );
}