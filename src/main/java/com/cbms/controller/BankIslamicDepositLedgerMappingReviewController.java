package com.cbms.controller;

import com.cbms.service.BankIslamicDepositLedgerMappingReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Review of proposed GL account mappings only. No posting or deposit activation endpoints. */
@RestController
@RequestMapping("/api/islamic/deposits")
public class BankIslamicDepositLedgerMappingReviewController {
    private final BankIslamicDepositLedgerMappingReviewService service;
    public BankIslamicDepositLedgerMappingReviewController(BankIslamicDepositLedgerMappingReviewService service) {
        this.service = service;
    }
    @GetMapping("/{fdId}/ledger-mapping")
    public List<Map<String, Object>> list(@PathVariable Long fdId) { return service.list(fdId); }
    @PostMapping("/{fdId}/ledger-mapping")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable Long fdId,
             @RequestBody BankIslamicDepositLedgerMappingReviewService.Proposal proposal, Authentication actor) {
        return service.create(fdId, proposal, actor);
    }
    @PostMapping("/ledger-mapping/{id}/submit")
    public Map<String, Object> submit(@PathVariable Long id, Authentication actor) {
        return service.submit(id, actor);
    }
    @PostMapping("/ledger-mapping/{id}/decision")
    public Map<String, Object> decide(@PathVariable Long id,
              @RequestBody BankIslamicDepositLedgerMappingReviewService.Decision decision, Authentication actor) {
        return service.decide(id, decision, actor);
    }
}
