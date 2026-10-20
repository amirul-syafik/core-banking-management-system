package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankCollateralCoverageItemResponse(
        Long linkId,
        Long collateralId,
        String collateralRef,
        BigDecimal allocatedAmount,
        BigDecimal latestForcedSaleValue,
        LocalDate latestValuationDate,
        boolean valuationStale,
        String collateralStatus
) {}
