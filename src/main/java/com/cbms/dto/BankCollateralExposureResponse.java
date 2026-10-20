package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankCollateralExposureResponse(
        Long collateralId,
        String collateralRef,
        String collateralStatus,
        BigDecimal latestForcedSaleValue,
        LocalDate latestValuationDate,
        boolean valuationStale,
        BigDecimal activeAllocatedAmount,
        BigDecimal availableAmount,
        BigDecimal overAllocatedAmount,
        long activeLinkCount,
        String exposureStatus
) {}
