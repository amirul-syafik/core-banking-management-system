package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankFixedDepositResponse(
        Long fdId,
        String fdReference,
        Long customerId,
        Long productId,
        Long schemeId,
        String fdType,
        String currency,
        BigDecimal principalAmount,
        Integer tenorMonths,
        LocalDate placementDate,
        LocalDate maturityDate,
        String rateType,
        BigDecimal interestRate,
        String status,
        String remarks
) {
}