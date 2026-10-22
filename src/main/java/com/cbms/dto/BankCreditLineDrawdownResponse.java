package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BankCreditLineDrawdownResponse(
        Long drawId,
        String drawReference,
        Long facilityId,
        BigDecimal drawAmount,
        String currency,
        String status,
        BigDecimal utilizedAmount,
        BigDecimal availableLimit,
        OffsetDateTime createdDate,
        String createdBy
) {
}