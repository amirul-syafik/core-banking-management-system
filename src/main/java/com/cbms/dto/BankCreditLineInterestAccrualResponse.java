package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BankCreditLineInterestAccrualResponse(
        Long accrualId,
        Long facilityId,
        LocalDate accrualDate,
        BigDecimal principalAmount,
        BigDecimal annualRatePct,
        Integer dayBasis,
        BigDecimal dailyInterestAmount,
        String status,
        OffsetDateTime createdDate,
        String createdBy
) {
}