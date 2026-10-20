package com.cbms.repository;

import com.cbms.entity.BankWorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface BankWorkflowDefinitionRepository extends JpaRepository<BankWorkflowDefinition, Long> {
    boolean existsByWorkflowCd(String workflowCd);

    Optional<BankWorkflowDefinition> findByWorkflowCd(String workflowCd);

    List<BankWorkflowDefinition> findAllByOrderByWorkflowCdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BankWorkflowDefinition> findWithLockByWorkflowId(Long workflowId);
}
