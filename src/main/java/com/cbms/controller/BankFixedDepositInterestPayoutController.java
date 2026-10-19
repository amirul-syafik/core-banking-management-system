package com.cbms.controller;

import com.cbms.dto.BankFixedDepositInterestPayoutRequest;
import com.cbms.dto.BankFixedDepositInterestPayoutResponse;
import com.cbms.service.BankFixedDepositInterestPayoutService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/fixed-deposits")
public class BankFixedDepositInterestPayoutController {

    private final BankFixedDepositInterestPayoutService service;

    public BankFixedDepositInterestPayoutController(
            BankFixedDepositInterestPayoutService service
    ) {
        this.service = service;
    }

    @PostMapping("/{fdId}/interest/payout")
    @ResponseStatus(HttpStatus.CREATED)
    public BankFixedDepositInterestPayoutResponse payout(
            @PathVariable Long fdId,
            @Valid @RequestBody BankFixedDepositInterestPayoutRequest request,
            Authentication authentication
    ) {
        return service.payout(fdId, request, authentication);
    }

    @GetMapping("/{fdId}/interest/payouts")
    public List<BankFixedDepositInterestPayoutResponse> history(
            @PathVariable Long fdId
    ) {
        return service.history(fdId);
    }

    @GetMapping("/{fdId}/interest/unpaid-total")
    public BigDecimal unpaidTotal(
            @PathVariable Long fdId
    ) {
        return service.unpaidInterest(fdId);
    }
}