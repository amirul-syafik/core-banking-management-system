package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.BankCreditLineMonitoringService;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-lines")
public class BankCreditLineMonitoringController {

    private final BankCreditLineMonitoringService service;

    public BankCreditLineMonitoringController(
            BankCreditLineMonitoringService service
    ) {
        this.service = service;
    }

    @GetMapping("/monitoring")
    public List<BankCreditLineMonitoringResponse> list() {
        return service.list();
    }

    @GetMapping("/facilities/{facId}/monitoring")
    public BankCreditLineMonitoringResponse get(
            @PathVariable Long facId
    ) {
        return service.get(facId);
    }

    @PostMapping("/facilities/{facId}/suspend")
    public BankCreditLineMonitoringResponse suspend(
            @PathVariable Long facId,
            @Valid @RequestBody BankCreditLineStatusActionRequest request,
            Authentication authentication
    ) {
        return service.suspend(facId, request, authentication);
    }

    @PostMapping("/facilities/{facId}/reactivate")
    public BankCreditLineMonitoringResponse reactivate(
            @PathVariable Long facId,
            @Valid @RequestBody BankCreditLineStatusActionRequest request,
            Authentication authentication
    ) {
        return service.reactivate(facId, request, authentication);
    }

    @PostMapping("/facilities/{facId}/close")
    public BankCreditLineMonitoringResponse close(
            @PathVariable Long facId,
            @Valid @RequestBody BankCreditLineStatusActionRequest request,
            Authentication authentication
    ) {
        return service.close(facId, request, authentication);
    }

    @GetMapping("/facilities/{facId}/status-history")
    public List<BankCreditLineStatusHistoryResponse> history(
            @PathVariable Long facId
    ) {
        return service.history(facId);
    }
}