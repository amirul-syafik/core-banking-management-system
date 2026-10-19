package com.cbms.dto;

public record BankFixedDepositPrematureWithdrawalRequest(
        Long destinationAccountId,
        String remarks
) {
}