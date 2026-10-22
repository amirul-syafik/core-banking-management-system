package com.cbms.controller;

import com.cbms.dto.TransactionAuditEventResponse;
import com.cbms.service.TransactionAuditService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/transaction-audit")
public class TransactionAuditController {
    private final TransactionAuditService service;

    public TransactionAuditController(TransactionAuditService service) {
        this.service = service;
    }

    @GetMapping
    public Page<TransactionAuditEventResponse> search(
            @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "") String reference,
            @RequestParam(defaultValue = "") String action,
            @RequestParam(defaultValue = "") String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.search(accountId, reference, action, actor, from, to, page, size);
    }
}
