package com.cbms.repository;

import com.cbms.entity.BankWorkflowHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BankWorkflowAuditRepository
        extends JpaRepository<BankWorkflowHistory, Long>,
        JpaSpecificationExecutor<BankWorkflowHistory> {
}
