package com.cbms.controller;

import com.cbms.dto.BankCreditLineRepaymentRequest;
import com.cbms.dto.BankCreditLineRepaymentResponse;
import com.cbms.service.BankCreditLineRepaymentService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-lines/facilities/{facId}/repayments")
public class BankCreditLineRepaymentController {

    private final BankCreditLineRepaymentService service;

    public BankCreditLineRepaymentController(
            BankCreditLineRepaymentService service
    ) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankCreditLineRepaymentResponse repay(
            @PathVariable Long facId,
            @Valid @RequestBody BankCreditLineRepaymentRequest request,
            Authentication authentication
    ) {
        return service.repay(facId, request, authentication);
    }

    @GetMapping
    public List<BankCreditLineRepaymentResponse> list(
            @PathVariable Long facId
    ) {
        return service.getRepayments(facId);
    }
}