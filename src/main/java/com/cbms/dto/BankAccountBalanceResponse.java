package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BankAccountBalanceResponse(
        Long acctId,
        String acctNo,
        String ccyCd,
        BigDecimal ledgerBal,
        BigDecimal availBal,
        OffsetDateTime updDt,
        String updBy
) {
}