package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankFixedDepositPlacementResponse(
        Long placementId,
        String placementReference,
        Long fixedDepositId,
        Long sourceAccountId,
        BigDecimal placementAmount,
        String currency,
        LocalDate placementDate,
        LocalDate maturityDate,
        String postingReference,
        String placementStatus,
        String fixedDepositStatus
) {
}