package com.cbms.controller;

import com.cbms.dto.CustomerKycRequest;
import com.cbms.entity.CustomerKyc;
import com.cbms.service.CustomerKycService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{custId}/kyc-assessments")
public class CustomerKycController {

    private final CustomerKycService kycService;

    public CustomerKycController(CustomerKycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerKyc createAssessment(
            @PathVariable Long custId,
            @Valid @RequestBody CustomerKycRequest request) {

        return kycService.createAssessment(custId, request);
    }

    @GetMapping
    public List<CustomerKyc> getAssessments(
            @PathVariable Long custId) {

        return kycService.getAssessments(custId);
    }
}