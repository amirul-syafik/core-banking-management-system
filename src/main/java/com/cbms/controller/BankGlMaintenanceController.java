package com.cbms.controller;

import com.cbms.service.BankGlMaintenanceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** GL catalog configuration only. There is deliberately no journal posting endpoint. */
@RestController
@RequestMapping("/api/gl-maintenance")
public class BankGlMaintenanceController {
    private final BankGlMaintenanceService service;
    public BankGlMaintenanceController(BankGlMaintenanceService service) { this.service = service; }

    public record ChangeRequest(String action, String glCode, String glName,
                                String glType, String currency, String reason) {}
    public record DecisionRequest(Boolean approve, String remarks) {}

    @GetMapping("/accounts")
    public Map<String, Object> accounts(@RequestParam(defaultValue = "") String query,
                                        @RequestParam(defaultValue = "0") int page) {
        return service.accounts(query, page);
    }

    @GetMapping("/requests")
    public Map<String, Object> requests(@RequestParam(defaultValue = "ALL") String status,
                                        @RequestParam(defaultValue = "0") int page) {
        return service.requests(status, page);
    }

    @PostMapping("/requests")
    public Map<String, Object> propose(@RequestBody ChangeRequest request, Authentication authentication) {
        return service.propose(request, authentication);
    }

    @PostMapping("/requests/{id}/decision")
    public Map<String, Object> decision(@PathVariable long id,
                                        @RequestBody DecisionRequest request, Authentication authentication) {
        return service.decide(id, request, authentication);
    }
}
