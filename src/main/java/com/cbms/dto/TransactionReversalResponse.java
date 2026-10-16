package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionReversalResponse(
        Long reversalTxnId,
        String reversalTxnRef,
        Long originalTxnId,
        String originalTxnTyp,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        String status,
        BigDecimal sourceBalance,
        BigDecimal destinationBalance,
        OffsetDateTime createdAt
) {
}