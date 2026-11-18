package com.cbms.controller;

import com.cbms.service.BankIslamicDepositPostingReadinessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only readiness report; deliberately no POST, PATCH or posting route. */
@RestController
@RequestMapping("/api/islamic/deposits")
public class BankIslamicDepositPostingReadinessController {
    private final BankIslamicDepositPostingReadinessService readiness;

    public BankIslamicDepositPostingReadinessController(BankIslamicDepositPostingReadinessService readiness) {
        this.readiness = readiness;
    }

    @GetMapping("/{fdId}/posting-readiness")
    public BankIslamicDepositPostingReadinessService.Report inspect(@PathVariable Long fdId) {
        return readiness.inspect(fdId);
    }
}
