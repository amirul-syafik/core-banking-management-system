package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionLedgerEntryResponse(
        Long entryId,
        Long glId,
        String glCd,
        String glNm,
        Long acctId,
        String entrySide,
        BigDecimal entryAmt,
        OffsetDateTime crtDt
) {
}