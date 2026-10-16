package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record TransactionDetailResponse(
        Long txnId,
        String txnRef,
        Long acctId,
        Long destAcctId,
        String txnTyp,
        BigDecimal txnAmt,
        String ccyCd,
        String txnSts,
        OffsetDateTime crtDt,
        String crtBy,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        boolean balanced,
        List<TransactionLedgerEntryResponse> ledgerEntries
) {
}
