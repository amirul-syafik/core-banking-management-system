package com.cbms.controller;

import com.cbms.dto.BankAccountCreateRequest;
import com.cbms.dto.BankAccountResponse;
import com.cbms.service.BankAccountService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.data.domain.Page;

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

    @GetMapping("/accounts/search")
    public Page<BankAccountResponse> search(
            @RequestParam(required = false) String accountNo,
            @RequestParam(required = false) String cifNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return accountService.search(accountNo, cifNo, status, page, size);
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
