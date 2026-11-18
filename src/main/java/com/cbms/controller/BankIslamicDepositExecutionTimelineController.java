package com.cbms.controller;

import com.cbms.service.BankIslamicDepositExecutionTimelineService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Documentary sequencing only. Intentionally no contract execute or GL-post endpoint. */
@RestController
@RequestMapping("/api/islamic/deposits")
public class BankIslamicDepositExecutionTimelineController {
    private final BankIslamicDepositExecutionTimelineService service;
    public BankIslamicDepositExecutionTimelineController(BankIslamicDepositExecutionTimelineService service) {
        this.service = service;
    }
    @GetMapping("/{fdId}/execution-timeline")
    public List<Map<String, Object>> list(@PathVariable Long fdId) { return service.list(fdId); }
    @PostMapping("/{fdId}/execution-timeline")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable Long fdId,
            @RequestBody BankIslamicDepositExecutionTimelineService.Proposal proposal, Authentication actor) {
        return service.create(fdId, proposal, actor);
    }
    @PostMapping("/execution-timeline/{id}/submit")
    public Map<String, Object> submit(@PathVariable Long id, Authentication actor) {
        return service.submit(id, actor);
    }
    @PostMapping("/execution-timeline/{id}/decision")
    public Map<String, Object> decide(@PathVariable Long id,
            @RequestBody BankIslamicDepositExecutionTimelineService.Decision decision, Authentication actor) {
        return service.decide(id, decision, actor);
    }
}
