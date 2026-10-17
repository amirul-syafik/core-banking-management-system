package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.BankCreditLineLimitService;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-lines/facilities/{facId}")
public class BankCreditLineLimitController {

    private final BankCreditLineLimitService service;

    public BankCreditLineLimitController(
            BankCreditLineLimitService service
    ) {
        this.service = service;
    }

    @PostMapping("/activate")
    public BankCreditLineLimitResponse activate(
            @PathVariable Long facId,
            @Valid @RequestBody BankCreditLineActivationRequest request,
            Authentication authentication
    ) {
        return service.activate(facId, request, authentication);
    }

    @PostMapping("/limit-adjustments")
    public BankCreditLineLimitResponse adjustLimit(
            @PathVariable Long facId,
            @Valid @RequestBody BankCreditLineLimitAdjustmentRequest request,
            Authentication authentication
    ) {
        return service.adjustLimit(facId, request, authentication);
    }

    @GetMapping("/limit")
    public BankCreditLineLimitResponse getLimit(
            @PathVariable Long facId
    ) {
        return service.getLimit(facId);
    }

    @GetMapping("/limit-adjustments")
    public List<BankCreditLineLimitHistoryResponse> getHistory(
            @PathVariable Long facId
    ) {
        return service.getHistory(facId);
    }
}