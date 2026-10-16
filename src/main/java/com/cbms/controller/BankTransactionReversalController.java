package com.cbms.controller;

import com.cbms.dto.TransactionReversalRequest;
import com.cbms.dto.TransactionReversalResponse;
import com.cbms.service.BankTransactionReversalService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class BankTransactionReversalController {

    private final BankTransactionReversalService reversalService;

    public BankTransactionReversalController(
            BankTransactionReversalService reversalService
    ) {
        this.reversalService = reversalService;
    }

    @PostMapping("/{txnId}/reversals")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionReversalResponse reverse(
            @PathVariable Long txnId,
            @Valid @RequestBody TransactionReversalRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication
    ) {
        return reversalService.reverse(
                txnId,
                request,
                idempotencyKey,
                authentication
        );
    }
}
