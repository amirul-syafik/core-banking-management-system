package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.BankFinancingCollateralService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/financing-collaterals")
public class BankFinancingCollateralController {
    private final BankFinancingCollateralService service;

    public BankFinancingCollateralController(BankFinancingCollateralService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BankFinancingCollateralResponse> propose(@Valid @RequestBody BankFinancingCollateralRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.propose(request, auth));
    }

    @PostMapping("/{linkId}/activate")
    public BankFinancingCollateralResponse activate(@PathVariable Long linkId, Authentication auth) {
        return service.activate(linkId, auth);
    }

    @PostMapping("/{linkId}/release")
    public BankFinancingCollateralResponse release(@PathVariable Long linkId, @Valid @RequestBody BankFinancingCollateralReleaseRequest request, Authentication auth) {
        return service.release(linkId, request, auth);
    }

    @GetMapping("/{linkId}")
    public BankFinancingCollateralResponse get(@PathVariable Long linkId) {
        return service.get(linkId);
    }

    @GetMapping("/application/{applId}")
    public List<BankFinancingCollateralResponse> application(@PathVariable Long applId) {
        return service.byApplication(applId);
    }

    @GetMapping("/facility/{facId}")
    public List<BankFinancingCollateralResponse> facility(@PathVariable Long facId) {
        return service.byFacility(facId);
    }

    @GetMapping("/collateral/{collateralId}")
    public List<BankFinancingCollateralResponse> collateral(@PathVariable Long collateralId) {
        return service.byCollateral(collateralId);
    }
}
