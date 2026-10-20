package com.cbms.service;

import com.cbms.dto.BankWorkflowDecisionRequest;
import com.cbms.dto.BankWorkflowTaskResponse;
import com.cbms.entity.BankWorkflowHistory;
import com.cbms.entity.BankWorkflowInstance;
import com.cbms.entity.BankWorkflowStep;
import com.cbms.entity.BankWorkflowTask;
import com.cbms.repository.BankWorkflowHistoryRepository;
import com.cbms.repository.BankWorkflowInstanceRepository;
import com.cbms.repository.BankWorkflowStepRepository;
import com.cbms.repository.BankWorkflowTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BankWorkflowDecisionService {
    private final BankWorkflowTaskRepository tasks;
    private final BankWorkflowInstanceRepository instances;
    private final BankWorkflowStepRepository steps;
    private final BankWorkflowHistoryRepository histories;

    public BankWorkflowDecisionService(BankWorkflowTaskRepository tasks,
                                       BankWorkflowInstanceRepository instances,
                                       BankWorkflowStepRepository steps,
                                       BankWorkflowHistoryRepository histories) {
        this.tasks = tasks;
        this.instances = instances;
        this.steps = steps;
        this.histories = histories;
    }

    @Transactional
    public BankWorkflowTaskResponse approve(Long taskId, BankWorkflowDecisionRequest request,
                                             Authentication authentication) {
        return decide(taskId, request, authentication, true);
    }

    @Transactional
    public BankWorkflowTaskResponse reject(Long taskId, BankWorkflowDecisionRequest request,
                                            Authentication authentication) {
        return decide(taskId, request, authentication, false);
    }

    private BankWorkflowTaskResponse decide(Long taskId, BankWorkflowDecisionRequest request,
                                             Authentication auth, boolean approve) {
        String actor = authenticatedUsername(auth);
        // Maintain the lock order
        BankWorkflowTask task = tasks.findLocked(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow task not found"));
        BankWorkflowInstance instance = instances.findLocked(task.getInstance().getInstanceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow instance not found"));

        if (!List.of("PENDING", "IN_PROGRESS").contains(instance.getWorkflowSts())
                || !"CLAIMED".equals(task.getTaskSts())
                || !task.getStep().getStepNo().equals(instance.getCurrentStepNo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not an actionable claimed task");
        }
        if (!actor.equals(task.getAssignedTo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task must be claimed by current user");
        }
        if (actor.equals(instance.getSubmittedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Maker cannot approve or reject own submission");
        }
        String requiredRole = task.getAssignedRole();
        if (auth.getAuthorities().stream().noneMatch(a -> ("ROLE_" + requiredRole).equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Required approval role missing");
        }
        if (!"ACTIVE".equals(task.getStep().getStepSts())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval step is inactive");
        }
        // A checker may only approve once per step. Enforce across previous completed tasks.
        if (approve && tasks.existsByInstance_InstanceIdAndStep_StepIdAndTaskStsAndActionBy(
                instance.getInstanceId(), task.getStep().getStepId(), "APPROVED", actor)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checker already approved this step");
        }

        OffsetDateTime now = OffsetDateTime.now();
        String decision = approve ? "APPROVED" : "REJECTED";
        String previousInstanceStatus = instance.getWorkflowSts();
        task.setTaskSts(decision);
        task.setActionBy(actor);
        task.setActionDt(now);
        task.setActionRemarks(request.remarks());
        task.setUpdBy(actor);
        tasks.saveAndFlush(task);

        if (!approve) {
            instance.setWorkflowSts("REJECTED");
            instance.setCompletedBy(actor);
            instance.setCompletedDt(now);
            instance.setCurrentStepNo(null);
            instance.setUpdBy(actor);
            instances.saveAndFlush(instance);
            record(instance, task, "REJECT", previousInstanceStatus, "REJECTED", request.remarks(), actor);
            return toResponse(task);
        }

        record(instance, task, "APPROVE", previousInstanceStatus, "IN_PROGRESS", request.remarks(), actor);
        BankWorkflowStep currentStep = task.getStep();
        long approvals = tasks.countByInstance_InstanceIdAndStep_StepIdAndTaskSts(
                instance.getInstanceId(), currentStep.getStepId(), "APPROVED");
        if (approvals < currentStep.getRequiredApprovals()) {
            instance.setWorkflowSts("IN_PROGRESS");
            instance.setUpdBy(actor);
            instances.saveAndFlush(instance);
            queueNextTask(instance, currentStep, actor);
            return toResponse(task);
        }

        List<BankWorkflowStep> configured = steps.findByWorkflow_WorkflowIdOrderByStepNoAsc(
                instance.getWorkflow().getWorkflowId());
        BankWorkflowStep nextStep = configured.stream()
                .filter(s -> "ACTIVE".equals(s.getStepSts()) && s.getStepNo() > currentStep.getStepNo())
                .findFirst().orElse(null);
        if (nextStep != null) {
            if (nextStep.getStepNo() != currentStep.getStepNo() + 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow approval step sequence is invalid");
            }
            instance.setCurrentStepNo(nextStep.getStepNo());
            instance.setWorkflowSts("IN_PROGRESS");
            instance.setUpdBy(actor);
            instances.saveAndFlush(instance);
            queueNextTask(instance, nextStep, actor);
        } else {
            instance.setWorkflowSts("APPROVED");
            instance.setCompletedBy(actor);
            instance.setCompletedDt(now);
            instance.setCurrentStepNo(null);
            instance.setUpdBy(actor);
            instances.saveAndFlush(instance);
            record(instance, task, "COMPLETE", "IN_PROGRESS", "APPROVED", "All approval steps completed", actor);
        }
        return toResponse(task);
    }

    private void queueNextTask(BankWorkflowInstance instance, BankWorkflowStep step, String actor) {
        BankWorkflowTask pending = new BankWorkflowTask();
        pending.setTaskRef("WFT-" + UUID.randomUUID());
        pending.setInstance(instance);
        pending.setStep(step);
        pending.setAssignedRole(step.getApproverRole());
        pending.setTaskSts("PENDING");
        pending.setCrtBy(actor);
        pending.setUpdBy(actor);
        tasks.saveAndFlush(pending);
        record(instance, pending, "ASSIGN", "IN_PROGRESS", "IN_PROGRESS",
                "Next approval task queued for role " + step.getApproverRole(), actor);
    }

    private void record(BankWorkflowInstance instance, BankWorkflowTask task, String action,
                        String from, String to, String remarks, String actor) {
        BankWorkflowHistory history = new BankWorkflowHistory();
        history.setInstance(instance);
        history.setTaskId(task.getTaskId());
        history.setActionCd(action);
        history.setFromSts(from);
        history.setToSts(to);
        history.setActionRemarks(remarks);
        history.setActionBy(actor);
        histories.saveAndFlush(history);
    }

    private BankWorkflowTaskResponse toResponse(BankWorkflowTask task) {
        BankWorkflowInstance instance = task.getInstance();
        BankWorkflowStep step = task.getStep();
        return new BankWorkflowTaskResponse(task.getTaskId(), task.getTaskRef(),
                instance.getInstanceId(), instance.getInstanceRef(),
                instance.getWorkflow().getWorkflowId(), instance.getWorkflow().getWorkflowCd(),
                instance.getEntityId(), instance.getEntityRef(), step.getStepId(),
                step.getStepNo(), step.getStepNm(), task.getAssignedRole(),
                task.getAssignedTo(), task.getTaskSts(), task.getAssignedDt(), task.getClaimedDt(),
                task.getActionBy(), task.getActionDt(), task.getActionRemarks(),
                task.getCrtDt(), task.getCrtBy(), task.getUpdDt(), task.getUpdBy());
    }

    private String authenticatedUsername(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return auth.getName();
    }
}
