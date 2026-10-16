package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionHistoryResponse(
        Long txnId,
        String txnRef,
        Long acctId,
        Long destAcctId,
        String txnTyp,
        String direction,
        BigDecimal txnAmt,
        BigDecimal signedAmount,
        String ccyCd,
        String txnSts,
        OffsetDateTime crtDt,
        String crtBy
) {
}
