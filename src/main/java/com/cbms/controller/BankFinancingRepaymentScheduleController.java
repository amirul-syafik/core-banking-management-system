package com.cbms.controller;

import com.cbms.dto.BankFinancingScheduleGenerateRequest;
import com.cbms.entity.BankFinancingRepaymentSchedule;
import com.cbms.service.BankFinancingRepaymentScheduleService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/financing/facilities/{facId}/schedule")
public class BankFinancingRepaymentScheduleController {

    private final BankFinancingRepaymentScheduleService service;

    public BankFinancingRepaymentScheduleController(
            BankFinancingRepaymentScheduleService service
    ) {
        this.service = service;
    }

    @PostMapping("/generate")
    public List<BankFinancingRepaymentSchedule> generate(
            @PathVariable Long facId,
            @Valid @RequestBody BankFinancingScheduleGenerateRequest request,
            Authentication authentication
    ) {
        return service.generate(facId, request, authentication);
    }

    @GetMapping
    public List<BankFinancingRepaymentSchedule> getSchedule(
            @PathVariable Long facId
    ) {
        return service.getSchedule(facId);
    }
}
