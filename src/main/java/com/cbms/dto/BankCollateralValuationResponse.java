package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BankCollateralValuationResponse(
        Long valuationId, Long collateralId, String collateralRef,
        String valuationTyp, LocalDate valuationDt,
        BigDecimal marketValue, BigDecimal forcedSaleValue,
        String currencyCd, String valuationSource, String remarks,
        OffsetDateTime crtDt, String crtBy
) {
}
