package com.cbms.controller;

import com.cbms.dto.TransactionDetailResponse;
import com.cbms.dto.TransactionHistoryResponse;
import com.cbms.service.BankTransactionQueryService;

import org.springframework.data.domain.Page;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
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

    @GetMapping("/accounts/{acctId}/transactions/inquiry")
    public Page<TransactionHistoryResponse> inquireAccountTransactions(@PathVariable Long acctId,
                                                                       @RequestParam(defaultValue = "") String reference,
                                                                       @RequestParam(defaultValue = "") String type,
                                                                       @RequestParam(defaultValue = "") String status,
                                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "10") int size) {
        return queryService.inquireAccountTransactions(acctId, reference, type, status, from, to, page, size);
    }

    @GetMapping("/transactions/{txnId}")
    public TransactionDetailResponse getTransactionDetails(
            @PathVariable Long txnId
    ) {
        return queryService.getTransactionDetails(txnId);
    }
}
