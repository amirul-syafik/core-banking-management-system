package com.cbms.repository;

import com.cbms.entity.BankWorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankWorkflowStepRepository extends JpaRepository<BankWorkflowStep, Long> {
    boolean existsByWorkflow_WorkflowIdAndStepNo(Long workflowId, Integer stepNo);

    List<BankWorkflowStep> findByWorkflow_WorkflowIdOrderByStepNoAsc(Long workflowId);
}
