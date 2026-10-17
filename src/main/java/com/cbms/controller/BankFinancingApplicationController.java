package com.cbms.controller;

import com.cbms.dto.BankFinancingApplicationCreateRequest;
import com.cbms.entity.BankFinancingApplication;
import com.cbms.service.BankFinancingApplicationService;
import com.cbms.dto.BankFinancingApplicationValidationResponse;
import com.cbms.service.BankFinancingApplicationValidationService;
import com.cbms.dto.BankFinancingApprovalRequest;
import com.cbms.dto.BankFinancingRejectionRequest;
import com.cbms.entity.BankFinancingApplicationHistory;
import com.cbms.service.BankFinancingApplicationWorkflowService;
import com.cbms.entity.BankFinancingFacility;
import com.cbms.service.BankFinancingFacilityBookingService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/financing/applications")
public class BankFinancingApplicationController {

    private final BankFinancingApplicationService service;
    private final BankFinancingApplicationValidationService validationService;
    private final BankFinancingApplicationWorkflowService workflowService;
    private final BankFinancingFacilityBookingService bookingService;

    public BankFinancingApplicationController(
            BankFinancingApplicationService service,
            BankFinancingApplicationValidationService validationService,
            BankFinancingApplicationWorkflowService workflowService,
            BankFinancingFacilityBookingService bookingService
    ) {
        this.service = service;
        this.validationService = validationService;
        this.workflowService = workflowService;
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankFinancingApplication create(
            @Valid @RequestBody BankFinancingApplicationCreateRequest request,
            Authentication authentication
    ) {
        return service.create(request, authentication);
    }

    @GetMapping("/{applId}")
    public BankFinancingApplication get(
            @PathVariable Long applId
    ) {
        return service.get(applId);
    }

    @GetMapping
    public List<BankFinancingApplication> list(
            @RequestParam(required = false) Long custId,
            @RequestParam(required = false) String status
    ) {
        return service.list(custId, status);
    }

    @GetMapping("/{applId}/validate")
    public BankFinancingApplicationValidationResponse validate(
            @PathVariable Long applId
    ) {
        return validationService.validate(applId);
    }


    @PostMapping("/{applId}/submit")
    public BankFinancingApplication submit(
            @PathVariable Long applId,
            Authentication authentication
    ) {
        return workflowService.submit(applId, authentication);
    }

    @PostMapping("/{applId}/approve")
    public BankFinancingApplication approve(
            @PathVariable Long applId,
            @Valid @RequestBody BankFinancingApprovalRequest request,
            Authentication authentication
    ) {
        return workflowService.approve(
                applId,
                request,
                authentication
        );
    }

    @PostMapping("/{applId}/reject")
    public BankFinancingApplication reject(
            @PathVariable Long applId,
            @Valid @RequestBody BankFinancingRejectionRequest request,
            Authentication authentication
    ) {
        return workflowService.reject(
                applId,
                request,
                authentication
        );
    }

    @GetMapping("/{applId}/history")
    public List<BankFinancingApplicationHistory> history(
            @PathVariable Long applId
    ) {
        return workflowService.history(applId);
    }

    @PostMapping("/{applId}/book-facility")
    public BankFinancingFacility bookFacility(
            @PathVariable Long applId,
            Authentication authentication
    ) {
        return bookingService.book(applId, authentication);
    }

}