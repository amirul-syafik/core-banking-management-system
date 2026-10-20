package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.BankCollateralValuationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/collaterals/{collateralId}/valuations")
public class BankCollateralValuationController {
    private final BankCollateralValuationService service;

    public BankCollateralValuationController(BankCollateralValuationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BankCollateralValuationResponse> create(@PathVariable Long collateralId,
                                                                  @Valid @RequestBody BankCollateralValuationRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(collateralId, request, auth));
    }

    @GetMapping("/latest")
    public BankCollateralValuationResponse latest(@PathVariable Long collateralId) {
        return service.latest(collateralId);
    }

    @GetMapping("/{valuationId}")
    public BankCollateralValuationResponse get(@PathVariable Long collateralId, @PathVariable Long valuationId) {
        return service.get(collateralId, valuationId);
    }

    @GetMapping
    public Page<BankCollateralValuationResponse> history(@PathVariable Long collateralId,
                                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.history(collateralId, page, size);
    }
}
