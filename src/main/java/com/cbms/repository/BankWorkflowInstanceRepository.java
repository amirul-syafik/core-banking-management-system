package com.cbms.repository;

import com.cbms.entity.BankWorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BankWorkflowInstanceRepository extends JpaRepository<BankWorkflowInstance, Long> {
    @Query("""
            select (count(i) > 0) from BankWorkflowInstance i
            where i.workflow.workflowId = :workflowId
              and i.entityId = :entityId
              and i.workflowSts in ('PENDING', 'IN_PROGRESS')
            """)
    boolean existsActiveForEntity(@Param("workflowId") Long workflowId, @Param("entityId") Long entityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from BankWorkflowInstance i where i.instanceId = :id")
    Optional<BankWorkflowInstance> findLocked(@Param("id") Long id);

    Optional<BankWorkflowInstance> findByInstanceRef(String instanceRef);

    List<BankWorkflowInstance> findBySubmittedByOrderBySubmittedDtDesc(String submittedBy);
}
