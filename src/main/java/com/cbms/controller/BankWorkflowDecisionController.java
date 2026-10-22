package com.cbms.controller;

import com.cbms.dto.BankWorkflowDecisionRequest;
import com.cbms.dto.BankWorkflowTaskResponse;
import com.cbms.service.BankWorkflowDecisionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows/tasks")
public class BankWorkflowDecisionController {
    private final BankWorkflowDecisionService service;

    public BankWorkflowDecisionController(BankWorkflowDecisionService service) {
        this.service = service;
    }

    @PostMapping("/{taskId}/approve")
    public BankWorkflowTaskResponse approve(@PathVariable Long taskId,
                                            @Valid @RequestBody BankWorkflowDecisionRequest request, Authentication authentication) {
        return service.approve(taskId, request, authentication);
    }

    @PostMapping("/{taskId}/reject")
    public BankWorkflowTaskResponse reject(@PathVariable Long taskId,
                                           @Valid @RequestBody BankWorkflowDecisionRequest request, Authentication authentication) {
        return service.reject(taskId, request, authentication);
    }
}
