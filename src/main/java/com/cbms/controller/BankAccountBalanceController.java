package com.cbms.controller;

import com.cbms.dto.BankAccountBalanceResponse;
import com.cbms.service.BankAccountBalanceService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class BankAccountBalanceController {

    private final BankAccountBalanceService balanceService;

    public BankAccountBalanceController(
            BankAccountBalanceService balanceService
    ) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{acctId}/balance")
    public BankAccountBalanceResponse getBalance(
            @PathVariable Long acctId
    ) {
        return balanceService.getBalance(acctId);
    }
}
