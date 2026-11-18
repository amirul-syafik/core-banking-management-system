package com.cbms.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cbms.service.BankIslamicExecutionPolicyService;

/** Review-only policy register; no posting, settlement or trade endpoints. */
@RestController
@RequestMapping("/api/islamic/execution-policies")
public class BankIslamicExecutionPolicyController {
    private final BankIslamicExecutionPolicyService policies;
    public BankIslamicExecutionPolicyController(BankIslamicExecutionPolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam String contractType) {
        return policies.list(contractType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody BankIslamicExecutionPolicyService.Draft draft, Authentication actor) {
        return policies.create(draft, actor);
    }

    @PostMapping("/{policyId}/submit")
    public Map<String, Object> submit(@PathVariable Long policyId, Authentication actor) {
        return policies.submit(policyId, actor);
    }

    @PostMapping("/{policyId}/decision")
    public Map<String, Object> decide(@PathVariable Long policyId,
            @RequestBody BankIslamicExecutionPolicyService.Decision decision, Authentication actor) {
        return policies.decide(policyId, decision, actor);
    }
}
