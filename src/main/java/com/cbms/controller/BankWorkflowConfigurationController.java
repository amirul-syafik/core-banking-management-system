package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.BankWorkflowConfigurationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows/definitions")
public class BankWorkflowConfigurationController {
    private final BankWorkflowConfigurationService service;

    public BankWorkflowConfigurationController(BankWorkflowConfigurationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankWorkflowResponse create(@Valid @RequestBody BankWorkflowCreateRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @PostMapping("/{workflowId}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public BankWorkflowStepResponse addStep(@PathVariable Long workflowId, @Valid @RequestBody BankWorkflowStepCreateRequest request, Authentication authentication) {
        return service.addStep(workflowId, request, authentication);
    }

    @PostMapping("/{workflowId}/activate")
    public BankWorkflowResponse activate(@PathVariable Long workflowId, Authentication authentication) {
        return service.activate(workflowId, authentication);
    }

    @GetMapping("/{workflowId}")
    public BankWorkflowResponse get(@PathVariable Long workflowId) {
        return service.get(workflowId);
    }

    @GetMapping
    public List<BankWorkflowResponse> list() {
        return service.list();
    }
}
