package com.cbms.controller;

import com.cbms.service.BankIslamicDepositProfitTermsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Profit terms proposal review only; no operational posting endpoints. */
@RestController
@RequestMapping("/api/islamic/deposits")
public class BankIslamicDepositProfitTermsController {
    private final BankIslamicDepositProfitTermsService service;

    public BankIslamicDepositProfitTermsController(BankIslamicDepositProfitTermsService service) {
        this.service = service;
    }

    @GetMapping("/{fdId}/profit-terms")
    public List<Map<String, Object>> list(@PathVariable Long fdId) { return service.list(fdId); }

    @PostMapping("/{fdId}/profit-terms")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable Long fdId,
            @RequestBody BankIslamicDepositProfitTermsService.Proposal request, Authentication actor) {
        return service.create(fdId, request, actor);
    }

    @PostMapping("/profit-terms/{id}/submit")
    public Map<String, Object> submit(@PathVariable Long id, Authentication actor) {
        return service.submit(id, actor);
    }

    @PostMapping("/profit-terms/{id}/decision")
    public Map<String, Object> decide(@PathVariable Long id,
            @RequestBody BankIslamicDepositProfitTermsService.Decision request, Authentication actor) {
        return service.decide(id, request, actor);
    }
}
