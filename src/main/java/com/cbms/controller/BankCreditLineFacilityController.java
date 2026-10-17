package com.cbms.controller;

import com.cbms.dto.BankCreditLineFacilityCreateRequest;
import com.cbms.entity.BankCreditLineFacility;
import com.cbms.service.BankCreditLineFacilityService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-lines/facilities")
public class BankCreditLineFacilityController {

    private final BankCreditLineFacilityService service;

    public BankCreditLineFacilityController(
            BankCreditLineFacilityService service
    ) {
        this.service = service;
    }

    @PostMapping
    public BankCreditLineFacility create(
            @Valid @RequestBody BankCreditLineFacilityCreateRequest request,
            Authentication authentication
    ) {
        return service.create(request, authentication);
    }

    @GetMapping("/{facId}")
    public BankCreditLineFacility get(@PathVariable Long facId) {
        return service.get(facId);
    }

    @GetMapping("/customer/{custId}")
    public List<BankCreditLineFacility> getByCustomer(
            @PathVariable Long custId
    ) {
        return service.getByCustomer(custId);
    }
}