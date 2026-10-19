package com.cbms.controller;

import com.cbms.dto.BankFixedDepositPlacementRequest;
import com.cbms.dto.BankFixedDepositPlacementResponse;
import com.cbms.service.BankFixedDepositPlacementService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fixed-deposits")
public class BankFixedDepositPlacementController {

    private final BankFixedDepositPlacementService service;

    public BankFixedDepositPlacementController(
            BankFixedDepositPlacementService service
    ) {
        this.service = service;
    }

    @PostMapping("/{fdId}/place")
    @ResponseStatus(HttpStatus.CREATED)
    public BankFixedDepositPlacementResponse place(
            @PathVariable Long fdId,
            @Valid @RequestBody BankFixedDepositPlacementRequest request,
            Authentication authentication
    ) {
        return service.place(fdId, request, authentication);
    }

    @GetMapping("/{fdId}/placement")
    public BankFixedDepositPlacementResponse getPlacement(
            @PathVariable Long fdId
    ) {
        return service.getByDeposit(fdId);
    }
}