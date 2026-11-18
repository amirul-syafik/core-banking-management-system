package com.cbms.controller;

import com.cbms.service.BankIslamicContractProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/islamic/contracts")
public class BankIslamicContractProfileController {
    private final BankIslamicContractProfileService service;

    public BankIslamicContractProfileController(BankIslamicContractProfileService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam Long schemeId) {
        return service.list(schemeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody BankIslamicContractProfileService.DraftRequest request,
                                      Authentication actor) {
        return service.create(request, actor);
    }

    @PostMapping("/{id}/submit")
    public Map<String, Object> submit(@PathVariable Long id, Authentication actor) {
        return service.submit(id, actor);
    }

    @PostMapping("/{id}/decision")
    public Map<String, Object> decide(@PathVariable Long id,
            @RequestBody BankIslamicContractProfileService.DecisionRequest request, Authentication actor) {
        return service.decide(id, request, actor);
    }
}
