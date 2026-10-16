package com.cbms.controller;

import com.cbms.dto.TransferRequest;
import com.cbms.dto.TransferResponse;
import com.cbms.service.BankTransferService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class BankTransferController {

    private final BankTransferService transferService;

    public BankTransferController(BankTransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/{acctId}/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @PathVariable Long acctId,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication
    ) {
        return transferService.transfer(
                acctId,
                request,
                idempotencyKey,
                authentication
        );
    }
}
