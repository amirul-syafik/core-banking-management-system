package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.BankFloorStockService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-lines")
public class BankFloorStockController {

    private final BankFloorStockService service;

    public BankFloorStockController(BankFloorStockService service) {
        this.service = service;
    }

    @PostMapping("/facilities/{facId}/stocks")
    @ResponseStatus(HttpStatus.CREATED)
    public BankFloorStockResponse finance(
            @PathVariable Long facId,
            @Valid @RequestBody BankFloorStockCreateRequest request,
            Authentication authentication
    ) {
        return service.finance(facId, request, authentication);
    }

    @GetMapping("/facilities/{facId}/stocks")
    public List<BankFloorStockResponse> list(
            @PathVariable Long facId
    ) {
        return service.list(facId);
    }

    @GetMapping("/stocks/{stockId}")
    public BankFloorStockResponse get(
            @PathVariable Long stockId
    ) {
        return service.get(stockId);
    }

    @PostMapping("/stocks/{stockId}/settle")
    public BankFloorStockResponse settle(
            @PathVariable Long stockId,
            @Valid @RequestBody BankFloorStockSettlementRequest request,
            Authentication authentication
    ) {
        return service.settle(stockId, request, authentication);
    }
}