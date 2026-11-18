package com.cbms.controller;

import com.cbms.service.BankIslamicDepositExecutionEvidenceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Evidence and accounting-configuration review only: no posting routes. */
@RestController
@RequestMapping("/api/islamic/deposits")
public class BankIslamicDepositExecutionEvidenceController {
    private final BankIslamicDepositExecutionEvidenceService service;

    public BankIslamicDepositExecutionEvidenceController(BankIslamicDepositExecutionEvidenceService service) {
        this.service = service;
    }

    @GetMapping("/{fdId}/execution-evidence")
    public List<Map<String, Object>> list(@PathVariable Long fdId) { return service.list(fdId); }

    @PostMapping("/{fdId}/execution-evidence")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable Long fdId,
            @RequestBody BankIslamicDepositExecutionEvidenceService.Submission request, Authentication actor) {
        return service.create(fdId, request, actor);
    }

    @PostMapping("/execution-evidence/{id}/submit")
    public Map<String, Object> submit(@PathVariable Long id, Authentication actor) {
        return service.submit(id, actor);
    }

    @PostMapping("/execution-evidence/{id}/decision")
    public Map<String, Object> decide(@PathVariable Long id,
            @RequestBody BankIslamicDepositExecutionEvidenceService.Decision request, Authentication actor) {
        return service.decide(id, request, actor);
    }
}
