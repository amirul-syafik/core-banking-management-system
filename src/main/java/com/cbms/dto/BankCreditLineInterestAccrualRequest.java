package com.cbms.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record BankCreditLineInterestAccrualRequest(
        @NotNull LocalDate accrualDate
) {
}