package com.cbms.dto;

import java.math.BigDecimal;

public record BankCreditLineInterestSummaryResponse(
        Long facilityId,
        String facilityReference,
        String facilityType,
        String facilityStatus,
        BigDecimal currentPrincipal,
        BigDecimal annualRatePct,
        String rateType,
        String baseRateCode,
        BigDecimal marginPct,
        BigDecimal originationRatePct,
        Long accruedDays,
        BigDecimal totalAccruedInterest,
        BigDecimal totalAccruedInterestRounded
) {
}