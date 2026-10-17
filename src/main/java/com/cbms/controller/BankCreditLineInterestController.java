package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.BankCreditLineInterestService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-lines/facilities/{facId}/interest")
public class BankCreditLineInterestController {

    private final BankCreditLineInterestService service;

    public BankCreditLineInterestController(
            BankCreditLineInterestService service
    ) {
        this.service = service;
    }

    @PostMapping("/accrue")
    @ResponseStatus(HttpStatus.CREATED)
    public BankCreditLineInterestAccrualResponse accrue(
            @PathVariable Long facId,
            @Valid @RequestBody BankCreditLineInterestAccrualRequest request,
            Authentication authentication
    ) {
        return service.accrue(facId, request, authentication);
    }

    @GetMapping("/accruals")
    public List<BankCreditLineInterestAccrualResponse> list(
            @PathVariable Long facId
    ) {
        return service.list(facId);
    }

    @GetMapping("/summary")
    public BankCreditLineInterestSummaryResponse summary(
            @PathVariable Long facId
    ) {
        return service.summary(facId);
    }
}