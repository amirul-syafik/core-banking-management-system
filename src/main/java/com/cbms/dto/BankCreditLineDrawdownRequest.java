package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BankCreditLineDrawdownRequest(

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal drawAmount,

        @NotBlank
        @Size(max = 50)
        String drawReference,

        @Size(max = 1000)
        String remarks

) {
}