package com.cbms.controller;

import com.cbms.dto.BankAccountApplicationCreateRequest;
import com.cbms.dto.BankAccountApplicationResponse;
import com.cbms.service.BankAccountApplicationCompletionService;
import com.cbms.service.BankAccountApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.cbms.dto.BankAccountApplicationValidationResponse;
import com.cbms.service.BankAccountApplicationValidationService;
import com.cbms.dto.BankAccountApplicationReviewRequest;
import com.cbms.entity.BankAccountApplication;
import com.cbms.entity.BankAccountApplicationHistory;
import com.cbms.service.BankAccountApplicationWorkflowService;
import com.cbms.dto.BankAccountApplicationDetailsResponse;
import com.cbms.service.BankAccountApplicationDetailsService;

import java.util.List;

@RestController
@RequestMapping("/api/account-applications")
public class BankAccountApplicationController {

    private final BankAccountApplicationService service;
    private final BankAccountApplicationValidationService validationService;
    private final BankAccountApplicationWorkflowService workflowService;
    private final BankAccountApplicationCompletionService completionService;
    private final BankAccountApplicationDetailsService detailsService;

    public BankAccountApplicationController(
            BankAccountApplicationService service,
            BankAccountApplicationValidationService validationService,
            BankAccountApplicationWorkflowService workflowService,
            BankAccountApplicationCompletionService completionService,
            BankAccountApplicationDetailsService detailsService
    ) {
        this.service = service;
        this.validationService = validationService;
        this.workflowService = workflowService;
        this.completionService = completionService;
        this.detailsService = detailsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccountApplicationResponse create(
            @Valid @RequestBody BankAccountApplicationCreateRequest request,
            Authentication authentication
    ) {
        return service.create(request, authentication);
    }

    @GetMapping("/{applId}")
    public BankAccountApplicationResponse get(
            @PathVariable Long applId
    ) {
        return service.get(applId);
    }

    @GetMapping
    public List<BankAccountApplicationResponse> list(
            @RequestParam(required = false) Long custId,
            @RequestParam(required = false) String status
    ) {
        return service.list(custId, status);
    }

    @GetMapping("/{applId}/validate")
    public BankAccountApplicationValidationResponse validate(
            @PathVariable Long applId
    ) {
        return validationService.validate(applId);
    }

    @PostMapping("/{applId}/submit")
    public BankAccountApplication submit(
            @PathVariable Long applId,
            Authentication authentication
    ) {
        return workflowService.submit(applId, authentication);
    }

    @PostMapping("/{applId}/approve")
    public BankAccountApplication approve(
            @PathVariable Long applId,
            @Valid @RequestBody BankAccountApplicationReviewRequest request,
            Authentication authentication
    ) {
        return workflowService.approve(
                applId, request.remarks(), authentication
        );
    }

    @PostMapping("/{applId}/reject")
    public BankAccountApplication reject(
            @PathVariable Long applId,
            @Valid @RequestBody BankAccountApplicationReviewRequest request,
            Authentication authentication
    ) {
        return workflowService.reject(
                applId, request.remarks(), authentication
        );
    }

    @GetMapping("/{applId}/history")
    public java.util.List<BankAccountApplicationHistory> history(
            @PathVariable Long applId
    ) {
        return workflowService.history(applId);
    }

    @PostMapping("/{applId}/create-account")
    public BankAccountApplication createAccount(
            @PathVariable Long applId,
            Authentication authentication
    ) {
        return completionService.createAccount(
                applId,
                authentication
        );
    }

    @GetMapping("/{applId}/details")
    public BankAccountApplicationDetailsResponse details(
            @PathVariable Long applId
    ) {
        return detailsService.getDetails(applId);
    }
}