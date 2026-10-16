package com.cbms.controller;

import com.cbms.dto.BankPostingRequest;
import com.cbms.dto.BankPostingResponse;
import com.cbms.service.BankPostingService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class BankPostingController {

    private final BankPostingService postingService;

    public BankPostingController(BankPostingService postingService) {
        this.postingService = postingService;
    }

    @PostMapping("/{acctId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public BankPostingResponse deposit(
            @PathVariable Long acctId,
            @Valid @RequestBody BankPostingRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication
    ) {
        return postingService.post(
                acctId,
                "DEPOSIT",
                request.amount(),
                idempotencyKey,
                authentication
        );
    }

    @PostMapping("/{acctId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public BankPostingResponse withdraw(
            @PathVariable Long acctId,
            @Valid @RequestBody BankPostingRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication
    ) {
        return postingService.post(
                acctId,
                "WITHDRAWAL",
                request.amount(),
                idempotencyKey,
                authentication
        );
    }
}
