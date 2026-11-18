package com.cbms.controller;

import com.cbms.dto.BankFinancingRepriceResponse;
import com.cbms.service.BankFinancingFloatingRepricingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/financing/facilities/{facId}/reprice")
public class BankFinancingRateRepricingController {
    private final BankFinancingFloatingRepricingService service;

    public BankFinancingRateRepricingController(BankFinancingFloatingRepricingService service) {
        this.service = service;
    }

    @PostMapping
    public BankFinancingRepriceResponse reprice(@PathVariable Long facId, Authentication authentication) {
        return service.reprice(facId, LocalDate.now(), authentication.getName());
    }
}
