package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BankCreditLineLimitAdjustmentRequest(
        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal newLimit,

        @NotBlank
        @Size(max = 1000)
        String reason
) {
}