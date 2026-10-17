package com.cbms.controller;

import com.cbms.dto.BankFinancingFacilityClosureRequest;
import com.cbms.dto.BankFinancingFacilityClosureResponse;
import com.cbms.service.BankFinancingFacilityClosureService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/financing/facilities/{facId}")
public class BankFinancingFacilityClosureController {

    private final BankFinancingFacilityClosureService service;

    public BankFinancingFacilityClosureController(
            BankFinancingFacilityClosureService service
    ) {
        this.service = service;
    }

    @PostMapping("/close")
    public BankFinancingFacilityClosureResponse close(
            @PathVariable Long facId,
            @Valid @RequestBody BankFinancingFacilityClosureRequest request,
            Authentication authentication
    ) {
        return service.close(facId, request, authentication);
    }

    @GetMapping("/closure")
    public BankFinancingFacilityClosureResponse getClosure(
            @PathVariable Long facId
    ) {
        return service.getClosure(facId);
    }
}
