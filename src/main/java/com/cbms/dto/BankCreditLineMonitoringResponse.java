package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankCreditLineMonitoringResponse(
        Long facilityId,
        String facilityReference,
        Long customerId,
        String facilityType,
        String facilityStatus,
        String currency,
        BigDecimal approvedLimit,
        BigDecimal utilizedAmount,
        BigDecimal availableLimit,
        BigDecimal accruedInterest,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        Long daysToExpiry,
        Boolean expired,
        Boolean eligibleForClosure
) {}