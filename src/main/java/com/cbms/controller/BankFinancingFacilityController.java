package com.cbms.controller;

import com.cbms.dto.BankFinancingFacilityCreateRequest;
import com.cbms.entity.BankFinancingFacility;
import com.cbms.service.BankFinancingFacilityService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/financing/facilities")
public class BankFinancingFacilityController {

    private final BankFinancingFacilityService service;

    public BankFinancingFacilityController(
            BankFinancingFacilityService service
    ) {
        this.service = service;
    }

    @GetMapping("/{facId}")
    public BankFinancingFacility get(
            @PathVariable Long facId
    ) {
        return service.get(facId);
    }

    @GetMapping
    public List<BankFinancingFacility> list(
            @RequestParam(required = false) Long custId,
            @RequestParam(required = false) String status
    ) {
        return service.list(custId, status);
    }
}