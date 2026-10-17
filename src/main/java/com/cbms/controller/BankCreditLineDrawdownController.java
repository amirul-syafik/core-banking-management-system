package com.cbms.controller;

import com.cbms.dto.BankCreditLineDrawdownRequest;
import com.cbms.dto.BankCreditLineDrawdownResponse;
import com.cbms.service.BankCreditLineDrawdownService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-lines/facilities/{facId}/drawdowns")
public class BankCreditLineDrawdownController {

    private final BankCreditLineDrawdownService service;

    public BankCreditLineDrawdownController(
            BankCreditLineDrawdownService service
    ) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankCreditLineDrawdownResponse drawdown(
            @PathVariable Long facId,
            @Valid @RequestBody BankCreditLineDrawdownRequest request,
            Authentication authentication
    ) {
        return service.drawdown(facId, request, authentication);
    }

    @GetMapping
    public List<BankCreditLineDrawdownResponse> list(
            @PathVariable Long facId
    ) {
        return service.getDrawdowns(facId);
    }
}