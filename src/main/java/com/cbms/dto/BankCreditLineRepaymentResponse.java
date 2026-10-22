package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BankCreditLineRepaymentResponse(
        Long repayId,
        String repayReference,
        Long facilityId,
        BigDecimal repayAmount,
        BigDecimal principalAmount,
        String currency,
        String status,
        BigDecimal utilizedAmount,
        BigDecimal availableLimit,
        OffsetDateTime createdDate,
        String createdBy
) {
}