package com.cbms.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cbms.service.BankIslamicDepositExternalVerificationService;

/** GET-only status. No execution, approval, settlement or posting endpoints. */
@RestController
@RequestMapping("/api/islamic/deposits")
public class BankIslamicDepositExternalVerificationController {
    private final BankIslamicDepositExternalVerificationService verification;

    public BankIslamicDepositExternalVerificationController(
            BankIslamicDepositExternalVerificationService verification) {
        this.verification = verification;
    }

    @GetMapping("/{fdId}/external-verification")
    public BankIslamicDepositExternalVerificationService.Report inspect(@PathVariable Long fdId) {
        return verification.inspect(fdId);
    }
}
