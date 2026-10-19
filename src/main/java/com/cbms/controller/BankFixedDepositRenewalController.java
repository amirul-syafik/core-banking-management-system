package com.cbms.controller;

import com.cbms.dto.BankFixedDepositRenewalRequest;
import com.cbms.service.BankFixedDepositRenewalService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/fixed-deposits")
public class BankFixedDepositRenewalController {

    private final BankFixedDepositRenewalService renewalService;

    public BankFixedDepositRenewalController(
            BankFixedDepositRenewalService renewalService
    ) {
        this.renewalService = renewalService;
    }

    @PostMapping("/{fdId}/renew")
    public Map<String, Object> renew(
            @PathVariable Long fdId,
            @Valid @RequestBody BankFixedDepositRenewalRequest request,
            Authentication authentication
    ) {
        return renewalService.renew(
                fdId,
                request,
                authentication.getName()
        );
    }
}