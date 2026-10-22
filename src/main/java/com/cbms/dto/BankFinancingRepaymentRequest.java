package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BankFinancingRepaymentRequest(

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Size(max = 100)
        String idempotencyKey,

        @NotBlank
        @Size(max = 100)
        String paymentRef,

        @NotBlank
        String paymentChannel,

        @Size(max = 1000)
        String remarks
) {
}