package com.cbms.dto;

import java.math.BigDecimal;

public record BankCreditLineInterestSummaryResponse(
        Long facilityId,
        String facilityReference,
        String facilityType,
        String facilityStatus,
        BigDecimal currentPrincipal,
        BigDecimal annualRatePct,
        Long accruedDays,
        BigDecimal totalAccruedInterest,
        BigDecimal totalAccruedInterestRounded
) {
}