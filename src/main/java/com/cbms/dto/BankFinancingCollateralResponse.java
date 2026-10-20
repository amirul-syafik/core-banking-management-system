package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BankFinancingCollateralResponse(Long linkId, Long applId, String applRef, Long facId, String facRef,
                                              Long collateralId, String collateralRef, BigDecimal allocatedAmount,
                                              String linkSts, OffsetDateTime linkedDt, OffsetDateTime activatedDt,
                                              OffsetDateTime releasedDt, String remarks, String crtBy) {
}
