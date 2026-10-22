package com.cbms.dto;

import java.time.OffsetDateTime;

public record BankProductResponse(
        Long prodId,
        String prodCd,
        String prodNm,
        String prodCat,
        String bankingTyp,
        String ccyCd,
        String prodDesc,
        String prodSts,
        OffsetDateTime crtDt,
        String crtBy,
        OffsetDateTime updDt,
        String updBy
) {
}
