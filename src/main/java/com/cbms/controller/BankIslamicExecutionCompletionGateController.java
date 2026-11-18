package com.cbms.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cbms.service.BankIslamicExecutionCompletionGateService;

/** Read-only financial safety/sign-off report. */
@RestController
@RequestMapping("/api/islamic/deposits")
public class BankIslamicExecutionCompletionGateController {
    private final BankIslamicExecutionCompletionGateService service;
    public BankIslamicExecutionCompletionGateController(BankIslamicExecutionCompletionGateService service) {
        this.service = service;
    }
    @GetMapping("/{fdId}/phase10-gates")
    public BankIslamicExecutionCompletionGateService.Report inspect(@PathVariable Long fdId) {
        return service.inspect(fdId);
    }
}
