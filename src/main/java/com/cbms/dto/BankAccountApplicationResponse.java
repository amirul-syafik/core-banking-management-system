package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BankAccountApplicationResponse(
        Long applId,
        String applRef,
        Long custId,
        Long prodId,
        Long schmId,
        String applSts,
        BigDecimal reqAmt,
        Integer reqTenor,
        String remarks,
        Long acctId,
        OffsetDateTime crtDt,
        String crtBy,
        OffsetDateTime updDt,
        String updBy
) {}