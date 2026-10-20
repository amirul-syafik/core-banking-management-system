package com.cbms.controller;

import com.cbms.dto.BankWorkflowHistoryResponse;
import com.cbms.dto.BankWorkflowInstanceResponse;
import com.cbms.dto.BankWorkflowSubmitRequest;
import com.cbms.service.BankWorkflowSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/workflows/instances")
public class BankWorkflowInstanceController {
    private final BankWorkflowSubmissionService service;

    public BankWorkflowInstanceController(BankWorkflowSubmissionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankWorkflowInstanceResponse submit(@Valid @RequestBody BankWorkflowSubmitRequest request,
                                               Authentication authentication) {
        return service.submit(request, authentication);
    }

    @GetMapping("/mine")
    public List<BankWorkflowInstanceResponse> mySubmissions(Authentication authentication) {
        return service.mySubmissions(authentication);
    }

    @GetMapping("/{instanceId}")
    public BankWorkflowInstanceResponse get(@PathVariable Long instanceId) {
        return service.get(instanceId);
    }

    @GetMapping("/{instanceId}/history")
    public List<BankWorkflowHistoryResponse> history(@PathVariable Long instanceId) {
        return service.history(instanceId);
    }
}
