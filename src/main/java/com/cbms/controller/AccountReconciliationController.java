package com.cbms.controller;

import com.cbms.dto.AccountReconciliationResponse;
import com.cbms.service.AccountReconciliationService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reconciliation")
public class AccountReconciliationController {

    private final AccountReconciliationService reconciliationService;

    public AccountReconciliationController(
            AccountReconciliationService reconciliationService
    ) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/accounts/{acctId}")
    public AccountReconciliationResponse reconcileAccount(
            @PathVariable Long acctId
    ) {
        return reconciliationService.reconcile(acctId);
    }
}