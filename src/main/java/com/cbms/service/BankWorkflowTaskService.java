package com.cbms.service;

import com.cbms.dto.BankWorkflowAssignTaskRequest;
import com.cbms.dto.BankWorkflowTaskResponse;
import com.cbms.entity.*;
import com.cbms.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BankWorkflowTaskService {
    private final BankWorkflowTaskRepository tasks;
    private final BankWorkflowInstanceRepository instances;
    private final BankWorkflowStepRepository steps;
    private final BankWorkflowHistoryRepository histories;
    private final AppUserRepository users;

    public BankWorkflowTaskService(BankWorkflowTaskRepository tasks,
                                   BankWorkflowInstanceRepository instances,
                                   BankWorkflowStepRepository steps,
                                   BankWorkflowHistoryRepository histories,
                                   AppUserRepository users) {
        this.tasks = tasks;
        this.instances = instances;
        this.steps = steps;
        this.histories = histories;
        this.users = users;
    }

    // Called inside the same transaction as submission, so instance/task/audit are atomic.
    @Transactional
    public BankWorkflowTaskResponse createFirstTask(BankWorkflowInstance instance, BankWorkflowStep step,
                                                    String actor) {
        if (!step.getWorkflow().getWorkflowId().equals(instance.getWorkflow().getWorkflowId())
                || !step.getStepNo().equals(instance.getCurrentStepNo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow step does not match instance");
        }
        if (tasks.existsByInstance_InstanceIdAndStep_StepId(instance.getInstanceId(), step.getStepId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow task already exists");
        }
        BankWorkflowTask task = new BankWorkflowTask();
        task.setTaskRef("WFT-" + UUID.randomUUID());
        task.setInstance(instance);
        task.setStep(step);
        task.setAssignedRole(step.getApproverRole());
        task.setTaskSts("PENDING");
        task.setCrtBy(actor);
        task.setUpdBy(actor);
        task = tasks.saveAndFlush(task);
        record(instance, task, "ASSIGN", "PENDING", "PENDING", "Task queued for role " + task.getAssignedRole(), actor);
        return toResponse(task);
    }

    @Transactional
    public BankWorkflowTaskResponse initialize(Long instanceId, Authentication auth) {
        String actor = username(auth);
        BankWorkflowInstance instance = instances.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow instance not found"));
        if (!"PENDING".equals(instance.getWorkflowSts()) || !Integer.valueOf(1).equals(instance.getCurrentStepNo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unprocessed first-step instances can be initialized");
        }
        BankWorkflowStep first = steps.findByWorkflow_WorkflowIdOrderByStepNoAsc(instance.getWorkflow().getWorkflowId())
                .stream().filter(s -> Integer.valueOf(1).equals(s.getStepNo()) && "ACTIVE".equals(s.getStepSts()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "First approval step missing"));
        return createFirstTask(instance, first, actor);
    }

    @Transactional(readOnly = true)
    public List<BankWorkflowTaskResponse> queue(Authentication auth) {
        String actor = username(auth);
        List<String> roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority()).filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5)).toList();
        if (roles.isEmpty()) return List.of();
        return tasks.findAvailable(actor, roles).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BankWorkflowTaskResponse> mine(Authentication auth) {
        return tasks.findByAssignedToAndTaskStsInOrderByAssignedDtAsc(
                username(auth), List.of("PENDING", "CLAIMED")).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BankWorkflowTaskResponse> byInstance(Long instanceId) {
        if (!instances.existsById(instanceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow instance not found");
        }
        return tasks.findByInstance_InstanceIdOrderByTaskIdAsc(instanceId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public BankWorkflowTaskResponse claim(Long taskId, Authentication auth) {
        String actor = username(auth);
        BankWorkflowTask task = locked(taskId);
        ensurePending(task);
        ensureDifferentMaker(task, actor);
        ensureRole(task, auth);
        if (task.getAssignedTo() != null && !actor.equals(task.getAssignedTo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task assigned to another user");
        }
        task.setAssignedTo(actor);
        task.setTaskSts("CLAIMED");
        task.setClaimedDt(OffsetDateTime.now());
        task.setUpdBy(actor);
        task = tasks.saveAndFlush(task);
        record(task.getInstance(), task, "CLAIM", "PENDING", "CLAIMED", "Task claimed", actor);
        return toResponse(task);
    }


    @Transactional
    public BankWorkflowTaskResponse assign(
            Long taskId,
            BankWorkflowAssignTaskRequest request,
            Authentication auth) {

        String actor = username(auth);

        BankWorkflowTask task = locked(taskId);
        ensurePending(task);

        String target = request.username().trim();
        ensureDifferentMaker(task, target);

        AppUser user = users.findByUsername(target)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Assignee not found"
                ));

        // Extract role to avoid referencing reassigned task in lambda
        String assignedRole = task.getAssignedRole();

        if (!Boolean.TRUE.equals(user.getEnabled())
                || user.getRoles().stream()
                .noneMatch(role -> assignedRole.equals(role.getRoleNm()))) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Assignee is disabled or lacks required role"
            );
        }

        task.setAssignedTo(target);
        task.setUpdBy(actor);

        task = tasks.saveAndFlush(task);

        record(
                task.getInstance(),
                task,
                "ASSIGN",
                "PENDING",
                "PENDING",
                request.remarks(),
                actor
        );

        return toResponse(task);
    }


    private BankWorkflowTask locked(Long id) {
        return tasks.findLocked(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow task not found"));
    }

    private void ensurePending(BankWorkflowTask task) {
        if (!"PENDING".equals(task.getTaskSts()) ||
                !List.of("PENDING", "IN_PROGRESS").contains(task.getInstance().getWorkflowSts())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is not pending");
        }
    }

    private void ensureDifferentMaker(BankWorkflowTask task, String actor) {
        if (actor.equals(task.getInstance().getSubmittedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Maker cannot be checker");
        }
    }

    private void ensureRole(BankWorkflowTask task, Authentication auth) {
        if (auth.getAuthorities().stream().noneMatch(a -> ("ROLE_" + task.getAssignedRole()).equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Required approval role missing");
        }
    }

    private String username(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return auth.getName();
    }

    private void record(BankWorkflowInstance instance, BankWorkflowTask task, String action,
                        String from, String to, String remarks, String actor) {
        BankWorkflowHistory h = new BankWorkflowHistory();
        h.setInstance(instance);
        h.setTaskId(task.getTaskId());
        h.setActionCd(action);
        h.setFromSts(from);
        h.setToSts(to);
        h.setActionRemarks(remarks);
        h.setActionBy(actor);
        histories.saveAndFlush(h);
    }

    private BankWorkflowTaskResponse toResponse(BankWorkflowTask t) {
        BankWorkflowInstance i = t.getInstance();
        BankWorkflowStep s = t.getStep();
        return new BankWorkflowTaskResponse(t.getTaskId(), t.getTaskRef(), i.getInstanceId(), i.getInstanceRef(),
                i.getWorkflow().getWorkflowId(), i.getWorkflow().getWorkflowCd(), i.getEntityId(), i.getEntityRef(),
                s.getStepId(), s.getStepNo(), s.getStepNm(), t.getAssignedRole(), t.getAssignedTo(),
                t.getTaskSts(), t.getAssignedDt(), t.getClaimedDt(), t.getActionBy(), t.getActionDt(),
                t.getActionRemarks(), t.getCrtDt(), t.getCrtBy(), t.getUpdDt(), t.getUpdBy());
    }
}
