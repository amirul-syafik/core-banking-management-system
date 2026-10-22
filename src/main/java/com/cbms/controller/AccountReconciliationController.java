package com.cbms.controller;

import com.cbms.dto.AccountReconciliationResponse;
import com.cbms.service.AccountReconciliationService;

import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/reconciliation")
public class AccountReconciliationController {

    private final AccountReconciliationService reconciliationService;

    public AccountReconciliationController(
            AccountReconciliationService reconciliationService
    ) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/accounts")
    public Page<AccountReconciliationResponse> search(
            @RequestParam(defaultValue = "") String accountNo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size) {
        return reconciliationService.search(accountNo, page, size);
    }

    @GetMapping("/accounts/{acctId}")
    public AccountReconciliationResponse reconcileAccount(
            @PathVariable Long acctId
    ) {
        return reconciliationService.reconcile(acctId);
    }
}