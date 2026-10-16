package com.cbms.controller;

import com.cbms.dto.TransactionDetailResponse;
import com.cbms.dto.TransactionHistoryResponse;
import com.cbms.service.BankTransactionQueryService;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BankTransactionQueryController {

    private final BankTransactionQueryService queryService;

    public BankTransactionQueryController(
            BankTransactionQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @GetMapping("/accounts/{acctId}/transactions")
    public Page<TransactionHistoryResponse> getAccountTransactions(
            @PathVariable Long acctId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return queryService.getAccountTransactions(
                acctId,
                page,
                size
        );
    }

    @GetMapping("/transactions/{txnId}")
    public TransactionDetailResponse getTransactionDetails(
            @PathVariable Long txnId
    ) {
        return queryService.getTransactionDetails(txnId);
    }
}
