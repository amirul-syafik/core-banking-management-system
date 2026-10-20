package com.cbms.repository;

import com.cbms.entity.BankWorkflowTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BankWorkflowTaskRepository extends JpaRepository<BankWorkflowTask, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BankWorkflowTask t where t.taskId = :id")
    Optional<BankWorkflowTask> findLocked(@Param("id") Long id);

    boolean existsByInstance_InstanceIdAndStep_StepId(Long instanceId, Long stepId);

    boolean existsByInstance_InstanceIdAndStep_StepIdAndTaskStsAndActionBy(
            Long instanceId, Long stepId, String taskSts, String actionBy);

    long countByInstance_InstanceIdAndStep_StepIdAndTaskSts(
            Long instanceId, Long stepId, String taskSts);

    List<BankWorkflowTask> findByInstance_InstanceIdOrderByTaskIdAsc(Long instanceId);

    @Query("""
            select t from BankWorkflowTask t
            where t.taskSts in ('PENDING', 'CLAIMED')
              and t.instance.workflowSts in ('PENDING', 'IN_PROGRESS')
              and t.instance.submittedBy <> :username
              and ((t.assignedTo = :username)
                   or (t.assignedTo is null and t.assignedRole in :roles))
            order by t.assignedDt asc, t.taskId asc
            """)
    List<BankWorkflowTask> findAvailable(@Param("username") String username,
                                         @Param("roles") List<String> roles);

    List<BankWorkflowTask> findByAssignedToAndTaskStsInOrderByAssignedDtAsc(
            String username, List<String> statuses);
}
