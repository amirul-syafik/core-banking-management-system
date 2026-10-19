package com.cbms.controller;

import com.cbms.dto.BankFixedDepositMaturityRequest;
import com.cbms.dto.BankFixedDepositMaturityResponse;
import com.cbms.dto.BankFixedDepositMaturityValidationResponse;
import com.cbms.service.BankFixedDepositMaturityService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fixed-deposits")
public class BankFixedDepositMaturityController {

    private final BankFixedDepositMaturityService service;

    public BankFixedDepositMaturityController(
            BankFixedDepositMaturityService service
    ) {
        this.service = service;
    }

    @PostMapping("/maturity/calculate")
    public BankFixedDepositMaturityResponse calculate(
            @Valid @RequestBody BankFixedDepositMaturityRequest request
    ) {
        return service.calculate(request);
    }

    @GetMapping("/{fdId}/maturity/preview")
    public BankFixedDepositMaturityResponse preview(
            @PathVariable Long fdId,
            @RequestParam(defaultValue = "NONE")
            String weekendConvention
    ) {
        return service.preview(fdId, weekendConvention);
    }

    @GetMapping("/{fdId}/maturity/validate")
    public BankFixedDepositMaturityValidationResponse validate(
            @PathVariable Long fdId
    ) {
        return service.validate(fdId);
    }
}