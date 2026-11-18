package com.cbms.controller;

import com.cbms.dto.BankFixedDepositMaturitySettlementRequest;
import com.cbms.service.BankFixedDepositMaturitySettlementService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/fixed-deposits")
public class BankFixedDepositMaturitySettlementController {
    private final BankFixedDepositMaturitySettlementService service;

    public BankFixedDepositMaturitySettlementController(BankFixedDepositMaturitySettlementService service) {
        this.service = service;
    }

    @PostMapping("/{fdId}/maturity/settle")
    public Map<String, Object> settle(@PathVariable Long fdId,
                                       @Valid @RequestBody BankFixedDepositMaturitySettlementRequest request,
                                       Authentication authentication) {
        return service.settle(fdId, request, authentication == null ? null : authentication.getName());
    }
}
