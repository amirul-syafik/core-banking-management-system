package com.cbms.controller;

import com.cbms.dto.BankAccountCreateRequest;
import com.cbms.dto.BankAccountResponse;
import com.cbms.service.BankAccountService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BankAccountController {

    private final BankAccountService accountService;

    public BankAccountController(BankAccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/customers/{custId}/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccountResponse create(
            @PathVariable Long custId,
            @Valid @RequestBody BankAccountCreateRequest request,
            Authentication authentication
    ) {
        return accountService.create(custId, request, authentication);
    }

    @GetMapping("/accounts/{acctId}")
    public BankAccountResponse get(@PathVariable Long acctId) {
        return accountService.get(acctId);
    }

    @GetMapping("/customers/{custId}/accounts")
    public List<BankAccountResponse> listByCustomer(
            @PathVariable Long custId
    ) {
        return accountService.listByCustomer(custId);
    }
}
