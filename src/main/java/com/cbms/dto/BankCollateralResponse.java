package com.cbms.dto;

import java.time.OffsetDateTime;

public record BankCollateralResponse(
        Long collateralId, String collateralRef, String collateralTyp, String collateralDesc,
        Long ownerCustId, String ownerCustNm, Long vehicleId, String chassisNo,
        String collateralSts, OffsetDateTime crtDt, String crtBy, OffsetDateTime updDt, String updBy
) {
}
