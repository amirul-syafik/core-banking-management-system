package com.cbms.controller;

import com.cbms.dto.BankFinancingOutstandingResponse;
import com.cbms.dto.BankFinancingSummaryResponse;
import com.cbms.service.BankFinancingOutstandingService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/financing/facilities/{facId}")
public class BankFinancingOutstandingController {

    private final BankFinancingOutstandingService service;

    public BankFinancingOutstandingController(
            BankFinancingOutstandingService service
    ) {
        this.service = service;
    }

    @GetMapping("/outstanding")
    public BankFinancingOutstandingResponse outstanding(
            @PathVariable Long facId
    ) {
        return service.getOutstanding(facId);
    }

    @GetMapping("/summary")
    public BankFinancingSummaryResponse summary(
            @PathVariable Long facId
    ) {
        return service.getSummary(facId);
    }
}
