package com.cbms.controller;

import com.cbms.dto.BankFinancingDisbursementRequest;
import com.cbms.dto.BankFinancingDisbursementResponse;
import com.cbms.service.BankFinancingDisbursementService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/financing/facilities/{facId}/disbursement")
public class BankFinancingDisbursementController {

    private final BankFinancingDisbursementService service;

    public BankFinancingDisbursementController(
            BankFinancingDisbursementService service
    ) {
        this.service = service;
    }

    @PostMapping
    public BankFinancingDisbursementResponse disburse(
            @PathVariable Long facId,
            @Valid @RequestBody BankFinancingDisbursementRequest request,
            Authentication authentication
    ) {
        return service.disburse(facId, request, authentication);
    }

    @GetMapping
    public BankFinancingDisbursementResponse get(
            @PathVariable Long facId
    ) {
        return service.getByFacility(facId);
    }
}
