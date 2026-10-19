package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankFixedDepositInterestPayoutResponse(
        Long payoutId,
        String payoutReference,
        Long fixedDepositId,
        Long destinationAccountId,
        BigDecimal payoutAmount,
        LocalDate payoutDate,
        String transactionReference,
        String status,
        Integer accrualCount
) {}