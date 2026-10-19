package com.cbms.controller;

import com.cbms.dto.BankFixedDepositAccrualResponse;
import com.cbms.dto.BankFixedDepositInterestRequest;
import com.cbms.dto.BankFixedDepositInterestResponse;
import com.cbms.service.BankFixedDepositAccrualService;
import com.cbms.service.BankFixedDepositInterestService;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/fixed-deposits")
public class BankFixedDepositInterestController {

    private final BankFixedDepositInterestService interestService;
    private final BankFixedDepositAccrualService accrualService;

    public BankFixedDepositInterestController(
            BankFixedDepositInterestService interestService,
            BankFixedDepositAccrualService accrualService
    ) {
        this.interestService = interestService;
        this.accrualService = accrualService;
    }

    @PostMapping("/interest/calculate")
    public BankFixedDepositInterestResponse calculate(
            @Valid @RequestBody BankFixedDepositInterestRequest request
    ) {
        return interestService.calculate(request);
    }

    @PostMapping("/{fdId}/interest/accrue")
    public BankFixedDepositAccrualResponse accrue(
            @PathVariable Long fdId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate accrualDate,
            Authentication authentication
    ) {
        return accrualService.accrue(
                fdId,
                accrualDate,
                authentication
        );
    }

    @GetMapping("/{fdId}/interest/accruals")
    public List<BankFixedDepositAccrualResponse> history(
            @PathVariable Long fdId
    ) {
        return accrualService.history(fdId);
    }

    @GetMapping("/{fdId}/interest/accrued-total")
    public BigDecimal totalAccrued(
            @PathVariable Long fdId
    ) {
        return accrualService.totalAccrued(fdId);
    }
}