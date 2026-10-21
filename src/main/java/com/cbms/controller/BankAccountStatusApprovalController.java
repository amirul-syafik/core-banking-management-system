package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.BankAccountStatusApprovalService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts/{acctId}/status-requests")
public class BankAccountStatusApprovalController {
    private final BankAccountStatusApprovalService service;

    public BankAccountStatusApprovalController(BankAccountStatusApprovalService service) {
        this.service = service;
    }

    @GetMapping
    public List<BankAccountStatusRequestView> list(@PathVariable Long acctId, Authentication auth) {
        return service.list(acctId, auth);
    }

    @PostMapping
    public BankAccountStatusRequestView propose(@PathVariable Long acctId, @Valid @RequestBody BankAccountStatusProposal body, Authentication auth) {
        return service.propose(acctId, body, auth);
    }

    @PostMapping("/{requestId}/decision")
    public BankAccountStatusRequestView decide(@PathVariable Long acctId, @PathVariable Long requestId, @Valid @RequestBody BankAccountStatusDecision body, Authentication auth) {
        return service.decide(acctId, requestId, body, auth);
    }
}
