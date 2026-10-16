package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransferResponse(
        Long txnId,
        String txnRef,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        String currency,
        String status,
        BigDecimal sourceBalance,
        BigDecimal destinationBalance,
        OffsetDateTime createdAt
) {
}