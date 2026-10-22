package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankCreditLineLimitResponse(
        Long clFacId,
        String clFacRef,
        String facilityType,
        String facilityStatus,
        String currency,
        BigDecimal approvedLimit,
        BigDecimal utilizedAmount,
        BigDecimal availableLimit,
        LocalDate effectiveDate,
        LocalDate expiryDate
) {
}