package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BankPostingResponse(
        Long txnId,
        String txnRef,
        Long acctId,
        String txnTyp,
        BigDecimal amount,
        String currency,
        String status,
        BigDecimal ledgerBalance,
        BigDecimal availableBalance,
        OffsetDateTime createdAt
) {
}