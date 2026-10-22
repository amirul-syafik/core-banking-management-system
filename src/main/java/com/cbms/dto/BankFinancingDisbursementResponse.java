package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BankFinancingDisbursementResponse(
        Long disbId,
        Long facId,
        String disbRef,
        BigDecimal disbAmt,
        String disbSts,
        String beneficiaryName,
        String beneficiaryRef,
        OffsetDateTime postedDt
) {
}