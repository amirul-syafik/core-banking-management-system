package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.BankCollateralService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/collaterals")
public class BankCollateralController {
    private final BankCollateralService service;

    public BankCollateralController(BankCollateralService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BankCollateralResponse> create(@Valid @RequestBody BankCollateralRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, auth));
    }

    @GetMapping("/{collateralId}")
    public BankCollateralResponse get(@PathVariable Long collateralId) {
        return service.get(collateralId);
    }

    @GetMapping
    public Page<BankCollateralResponse> search(@RequestParam(required = false) Long ownerCustId,
                                               @RequestParam(required = false) Long vehicleId, @RequestParam(required = false) String collateralTyp,
                                               @RequestParam(required = false) String collateralSts, @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.search(ownerCustId, vehicleId, collateralTyp, collateralSts, page, size);
    }

    @PutMapping("/{collateralId}")
    public BankCollateralResponse update(@PathVariable Long collateralId,
                                         @Valid @RequestBody BankCollateralRequest request, Authentication auth) {
        return service.update(collateralId, request, auth);
    }

    @PatchMapping("/{collateralId}/status")
    public BankCollateralResponse status(@PathVariable Long collateralId,
                                         @Valid @RequestBody BankCollateralStatusRequest request, Authentication auth) {
        return service.changeStatus(collateralId, request, auth);
    }
}
