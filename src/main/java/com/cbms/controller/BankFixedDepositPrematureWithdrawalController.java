package com.cbms.controller;

import com.cbms.dto.BankFixedDepositPrematureWithdrawalRequest;
import com.cbms.service.BankFixedDepositPrematureWithdrawalService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/fixed-deposits")
public class BankFixedDepositPrematureWithdrawalController {

    private final BankFixedDepositPrematureWithdrawalService service;

    public BankFixedDepositPrematureWithdrawalController(
            BankFixedDepositPrematureWithdrawalService service
    ) {
        this.service = service;
    }

    @PostMapping("/{fdId}/premature-withdraw")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> withdraw(
            @PathVariable Long fdId,
            @RequestBody BankFixedDepositPrematureWithdrawalRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                service.withdraw(fdId, request, authentication)
        );
    }
}