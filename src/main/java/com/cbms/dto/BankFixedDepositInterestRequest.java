package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record BankFixedDepositInterestRequest(
        @NotNull
        @DecimalMin("0.01")
        java.math.BigDecimal principalAmount,

        @NotNull
        @DecimalMin("0.0")
        java.math.BigDecimal annualRate,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        @NotBlank
        @Pattern(regexp = "ACT_365_FIXED|ACT_360")
        String dayCountBasis
) {
}