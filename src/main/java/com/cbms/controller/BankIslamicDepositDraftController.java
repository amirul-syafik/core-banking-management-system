package com.cbms.controller;

import com.cbms.service.BankIslamicDepositDraftService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Read-only review endpoints. These endpoints never activate or post a deposit. */
@RestController
@RequestMapping("/api/islamic/deposits")
public class BankIslamicDepositDraftController {
    private final BankIslamicDepositDraftService service;

    public BankIslamicDepositDraftController(BankIslamicDepositDraftService service) {
        this.service = service;
    }

    @GetMapping("/schemes/{schemeId}/readiness")
    public BankIslamicDepositDraftService.Readiness readiness(@PathVariable Long schemeId) {
        return service.readiness(schemeId);
    }

    @GetMapping("/{fdId}/contract")
    public Map<String, Object> contract(@PathVariable Long fdId) {
        return service.depositContract(fdId);
    }
}
