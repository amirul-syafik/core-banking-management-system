package com.cbms.service;

import com.cbms.dto.*;
import com.cbms.entity.*;
import com.cbms.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BankWorkflowConfigurationService {
    private final BankWorkflowDefinitionRepository definitions;
    private final BankWorkflowStepRepository steps;

    public BankWorkflowConfigurationService(BankWorkflowDefinitionRepository definitions, BankWorkflowStepRepository steps) {
        this.definitions = definitions;
        this.steps = steps;
    }

    @Transactional
    public BankWorkflowResponse create(BankWorkflowCreateRequest request, Authentication authentication) {
        if (definitions.existsByWorkflowCd(request.workflowCd()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow code already exists");
        BankWorkflowDefinition entity = new BankWorkflowDefinition();
        entity.setWorkflowCd(request.workflowCd());
        entity.setWorkflowNm(request.workflowNm().trim());
        entity.setWorkflowDesc(request.workflowDesc());
        entity.setModuleCd(request.moduleCd());
        entity.setEntityTyp(request.entityTyp());
        entity.setWorkflowSts("INACTIVE");
        entity.setVersionNo(1);
        entity.setCrtBy(authentication.getName());
        entity.setUpdBy(authentication.getName());
        return map(definitions.saveAndFlush(entity), List.of());
    }

    @Transactional
    public BankWorkflowStepResponse addStep(Long workflowId, BankWorkflowStepCreateRequest request, Authentication authentication) {
        BankWorkflowDefinition workflow = lockWorkflow(workflowId);
        if (!"INACTIVE".equals(workflow.getWorkflowSts()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Steps cannot be changed after activation");
        if (steps.existsByWorkflow_WorkflowIdAndStepNo(workflowId, request.stepNo()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Step number already exists");
        BankWorkflowStep step = new BankWorkflowStep();
        step.setWorkflow(workflow);
        step.setStepNo(request.stepNo());
        step.setStepNm(request.stepNm().trim());
        step.setApproverRole(request.approverRole());
        step.setRequiredApprovals(request.requiredApprovals());
        step.setStepSts("ACTIVE");
        step.setCrtBy(authentication.getName());
        step.setUpdBy(authentication.getName());
        return mapStep(steps.saveAndFlush(step));
    }

    @Transactional
    public BankWorkflowResponse activate(Long workflowId, Authentication authentication) {
        BankWorkflowDefinition workflow = lockWorkflow(workflowId);
        if (!"INACTIVE".equals(workflow.getWorkflowSts()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow is already active");
        List<BankWorkflowStep> configured = steps.findByWorkflow_WorkflowIdOrderByStepNoAsc(workflowId);
        if (configured.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one approval step is required");
        int expected = 1;
        for (BankWorkflowStep step : configured) {
            if (!"ACTIVE".equals(step.getStepSts()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All configured steps must be active");
            if (step.getStepNo() != expected++)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approval steps must be sequential starting at 1");
        }
        workflow.setWorkflowSts("ACTIVE");
        workflow.setUpdBy(authentication.getName());
        definitions.saveAndFlush(workflow);
        return map(workflow, configured.stream().map(this::mapStep).toList());
    }

    @Transactional(readOnly = true)
    public BankWorkflowResponse get(Long workflowId) {
        BankWorkflowDefinition workflow = definitions.findById(workflowId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
        return map(workflow, steps.findByWorkflow_WorkflowIdOrderByStepNoAsc(workflowId).stream().map(this::mapStep).toList());
    }

    @Transactional(readOnly = true)
    public List<BankWorkflowResponse> list() {
        return definitions.findAllByOrderByWorkflowCdAsc().stream().map(w -> map(w, steps.findByWorkflow_WorkflowIdOrderByStepNoAsc(w.getWorkflowId()).stream().map(this::mapStep).toList())).toList();
    }

    private BankWorkflowDefinition lockWorkflow(Long id) {
        return definitions.findWithLockByWorkflowId(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
    }

    private BankWorkflowStepResponse mapStep(BankWorkflowStep s) {
        return new BankWorkflowStepResponse(s.getStepId(), s.getWorkflow().getWorkflowId(), s.getStepNo(), s.getStepNm(), s.getApproverRole(), s.getRequiredApprovals(), s.getStepSts(), s.getCrtDt(), s.getCrtBy());
    }

    private BankWorkflowResponse map(BankWorkflowDefinition w, List<BankWorkflowStepResponse> stepResponses) {
        return new BankWorkflowResponse(w.getWorkflowId(), w.getWorkflowCd(), w.getWorkflowNm(), w.getWorkflowDesc(), w.getModuleCd(), w.getEntityTyp(), w.getWorkflowSts(), w.getVersionNo(), w.getCrtDt(), w.getCrtBy(), w.getUpdDt(), w.getUpdBy(), stepResponses);
    }
}
