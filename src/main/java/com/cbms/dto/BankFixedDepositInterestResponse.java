package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankFixedDepositInterestResponse(
        BigDecimal principalAmount,
        BigDecimal annualRate,
        LocalDate startDate,
        LocalDate endDate,
        Long actualDays,
        String dayCountBasis,
        BigDecimal dailyInterest,
        BigDecimal totalInterest
) {}