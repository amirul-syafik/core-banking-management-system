package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BankFinancingFacilityClosureResponse(
        Long closureId,
        Long facId,
        String closureRef,
        String closureTyp,
        String facilityStatus,
        BigDecimal principalOutstanding,
        BigDecimal interestOutstanding,
        String remarks,
        OffsetDateTime closedDt,
        String closedBy
) {
}