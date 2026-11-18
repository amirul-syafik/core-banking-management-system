package com.cbms.controller;

import com.cbms.service.TransactionLedgerValidationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** On-demand, read-only journal diagnostics for administrators; never triggers posting. */
@RestController
@RequestMapping("/api/reconciliation/journal-integrity")
public class TransactionJournalIntegrityController {
    private final TransactionLedgerValidationService validator;

    public TransactionJournalIntegrityController(TransactionLedgerValidationService validator) {
        this.validator = validator;
    }

    @GetMapping("/{transactionId}")
    public TransactionLedgerValidationService.ValidationResult inspect(@PathVariable Long transactionId) {
        return validator.validate(transactionId);
    }
}
