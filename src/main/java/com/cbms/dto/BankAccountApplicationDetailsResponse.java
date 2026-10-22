package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record BankAccountApplicationDetailsResponse(

        Long applId,
        String applRef,
        String applSts,

        Long custId,
        String custNm,
        String custSts,

        String kycSts,
        String riskRtng,

        Long prodId,
        String prodCd,
        String prodNm,
        String prodCat,

        Long schmId,
        String schmCd,
        String schmNm,

        BigDecimal reqAmt,
        Integer reqTenor,
        String remarks,

        Long acctId,
        String acctNo,
        String acctTyp,
        String acctSts,

        String crtBy,
        OffsetDateTime crtDt,

        String submittedBy,
        OffsetDateTime submittedDt,

        String reviewedBy,
        OffsetDateTime reviewedDt,
        String reviewRemarks,

        List<BankAccountApplicationHistoryResponse> history

) {
}
