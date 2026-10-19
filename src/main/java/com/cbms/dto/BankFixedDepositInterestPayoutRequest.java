package com.cbms.dto;

import jakarta.validation.constraints.NotNull;

public record BankFixedDepositInterestPayoutRequest(
        @NotNull Long destinationAccountId
) {}