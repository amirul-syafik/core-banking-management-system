package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BankSchemeResponse(
        Long schmId,
        Long prodId,
        String schmCd,
        String schmNm,
        String schmDesc,
        BigDecimal minAmt,
        BigDecimal maxAmt,
        Integer minTenor,
        Integer maxTenor,
        String eligibilityDesc,
        String schmSts,
        OffsetDateTime crtDt,
        String crtBy,
        OffsetDateTime updDt,
        String updBy
) {}
