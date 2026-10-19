package com.cbms.controller;

import com.cbms.service.BankFixedDepositMaturityProcessingService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/fixed-deposits")
public class BankFixedDepositMaturityProcessingController {

    private final BankFixedDepositMaturityProcessingService service;

    public BankFixedDepositMaturityProcessingController(
            BankFixedDepositMaturityProcessingService service
    ) {
        this.service = service;
    }

    @PostMapping("/{fdId}/maturity/process")
    public Map<String, Object> process(
            @PathVariable Long fdId,
            Authentication authentication
    ) {
        return service.process(
                fdId,
                authentication.getName()
        );
    }
}
