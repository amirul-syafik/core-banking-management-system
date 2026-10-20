package com.cbms.service;

import com.cbms.dto.BankWorkflowHistoryResponse;
import com.cbms.dto.BankWorkflowInstanceResponse;
import com.cbms.dto.BankWorkflowSubmitRequest;
import com.cbms.entity.BankWorkflowDefinition;
import com.cbms.entity.BankWorkflowHistory;
import com.cbms.entity.BankWorkflowInstance;
import com.cbms.entity.BankWorkflowStep;
import com.cbms.repository.BankWorkflowDefinitionRepository;
import com.cbms.repository.BankWorkflowHistoryRepository;
import com.cbms.repository.BankWorkflowInstanceRepository;
import com.cbms.repository.BankWorkflowStepRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class BankWorkflowSubmissionService {
    private final BankWorkflowDefinitionRepository definitions;
    private final BankWorkflowStepRepository steps;
    private final BankWorkflowInstanceRepository instances;
    private final BankWorkflowHistoryRepository histories;
    private final BankWorkflowTaskService taskService;

    public BankWorkflowSubmissionService(
            BankWorkflowDefinitionRepository definitions,
            BankWorkflowStepRepository steps,
            BankWorkflowInstanceRepository instances,
            BankWorkflowHistoryRepository histories,
            BankWorkflowTaskService taskService) {
        this.definitions = definitions;
        this.steps = steps;
        this.instances = instances;
        this.histories = histories;
        this.taskService = taskService;
    }

    @Transactional
    public BankWorkflowInstanceResponse submit(BankWorkflowSubmitRequest request, Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        // Lock the definition to serialize submissions against the same configuration.
        // The database partial unique index is the final safeguard for duplicate active requests.
        BankWorkflowDefinition workflow = definitions.findWithLockByWorkflowId(request.workflowId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow definition not found"));

        if (!"ACTIVE".equals(workflow.getWorkflowSts())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow definition is not active");
        }

        List<BankWorkflowStep> configuredSteps = steps.findByWorkflow_WorkflowIdOrderByStepNoAsc(workflow.getWorkflowId());
        if (configuredSteps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow has no configured approval steps");
        }
        int expectedStepNo = 1;
        for (BankWorkflowStep step : configuredSteps) {
            if (!"ACTIVE".equals(step.getStepSts()) || !Integer.valueOf(expectedStepNo).equals(step.getStepNo())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow approval steps are invalid");
            }
            expectedStepNo++;
        }

        if (instances.existsActiveForEntity(workflow.getWorkflowId(), request.entityId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active workflow already exists for this entity");
        }

        String username = authentication.getName();
        BankWorkflowInstance instance = new BankWorkflowInstance();
        instance.setInstanceRef("WFI-" + UUID.randomUUID());
        instance.setWorkflow(workflow);
        instance.setWorkflowVersion(workflow.getVersionNo());
        instance.setEntityId(request.entityId());
        instance.setEntityRef(trimToNull(request.entityRef()));
        instance.setWorkflowSts("PENDING");
        instance.setCurrentStepNo(1);
        instance.setSubmittedBy(username);
        instance.setRemarks(trimToNull(request.remarks()));
        instance.setCrtBy(username);
        instance.setUpdBy(username);
        // Any constraint failure rolls back both instance and audit insertion.
        instance = instances.saveAndFlush(instance);

        BankWorkflowHistory history = new BankWorkflowHistory();
        history.setInstance(instance);
        history.setActionCd("SUBMIT");
        history.setToSts("PENDING");
        history.setActionRemarks(instance.getRemarks());
        history.setActionBy(username);
        histories.saveAndFlush(history);

        taskService.createFirstTask(instance, configuredSteps.get(0), username);
        return toResponse(instance);
    }

    @Transactional(readOnly = true)
    public BankWorkflowInstanceResponse get(Long instanceId) {
        return toResponse(instances.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow instance not found")));
    }

    @Transactional(readOnly = true)
    public List<BankWorkflowInstanceResponse> mySubmissions(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return instances.findBySubmittedByOrderBySubmittedDtDesc(authentication.getName())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BankWorkflowHistoryResponse> history(Long instanceId) {
        if (!instances.existsById(instanceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow instance not found");
        }
        return histories.findByInstance_InstanceIdOrderByActionDtAscHistIdAsc(instanceId)
                .stream().map(h -> new BankWorkflowHistoryResponse(
                        h.getHistId(), h.getInstance().getInstanceId(), h.getTaskId(),
                        h.getActionCd(), h.getFromSts(), h.getToSts(),
                        h.getActionRemarks(), h.getActionBy(), h.getActionDt()))
                .toList();
    }

    private BankWorkflowInstanceResponse toResponse(BankWorkflowInstance i) {
        BankWorkflowDefinition w = i.getWorkflow();
        return new BankWorkflowInstanceResponse(
                i.getInstanceId(), i.getInstanceRef(), w.getWorkflowId(), w.getWorkflowCd(),
                i.getWorkflowVersion(), w.getModuleCd(), w.getEntityTyp(),
                i.getEntityId(), i.getEntityRef(), i.getWorkflowSts(), i.getCurrentStepNo(),
                i.getSubmittedBy(), i.getSubmittedDt(), i.getCompletedBy(), i.getCompletedDt(),
                i.getRemarks(), i.getCrtDt(), i.getCrtBy(), i.getUpdDt(), i.getUpdBy());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
