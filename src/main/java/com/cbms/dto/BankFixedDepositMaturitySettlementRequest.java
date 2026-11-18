package com.cbms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BankFixedDepositMaturitySettlementRequest(
        @NotNull @Positive Long destinationAccountId,
        String remarks
) {}
