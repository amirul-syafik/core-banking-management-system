package com.cbms.controller;

import com.cbms.dto.BankFinancingRepaymentRequest;
import com.cbms.dto.BankFinancingRepaymentResponse;
import com.cbms.service.BankFinancingRepaymentService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/financing/facilities/{facId}/repayments")
public class BankFinancingRepaymentController {

    private final BankFinancingRepaymentService service;

    public BankFinancingRepaymentController(
            BankFinancingRepaymentService service
    ) {
        this.service = service;
    }

    @PostMapping
    public BankFinancingRepaymentResponse repay(
            @PathVariable Long facId,
            @Valid @RequestBody BankFinancingRepaymentRequest request,
            Authentication authentication
    ) {
        return service.repay(facId, request, authentication);
    }

    @GetMapping
    public List<BankFinancingRepaymentResponse> list(
            @PathVariable Long facId
    ) {
        return service.list(facId);
    }
}
