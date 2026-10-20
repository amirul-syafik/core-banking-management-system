
package com.cbms.repository;

import com.cbms.entity.BankWorkflowHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface BankWorkflowHistoryRepository
        extends JpaRepository<BankWorkflowHistory, Long>,
        JpaSpecificationExecutor<BankWorkflowHistory> {

    List<BankWorkflowHistory> findByInstance_InstanceIdOrderByActionDtAscHistIdAsc(
            Long instanceId
    );
}
