package com.cbms.dto;

import java.math.BigDecimal;
import java.util.List;

public record BankCollateralCoverageResponse(
        Long facId,
        String facRef,
        String facilityStatus,
        String currencyCd,
        BigDecimal approvedAmount,
        BigDecimal activeAllocatedAmount,
        BigDecimal coveragePercent,
        BigDecimal shortfallAmount,
        BigDecimal surplusAmount,
        String coverageStatus,
        List<BankCollateralCoverageItemResponse> collaterals
) {
}
