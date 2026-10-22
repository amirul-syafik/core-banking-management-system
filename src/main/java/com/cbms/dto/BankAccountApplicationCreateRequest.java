package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BankAccountApplicationCreateRequest(

        @NotNull
        @Positive
        Long custId,

        @NotNull
        @Positive
        Long prodId,

        @NotNull
        @Positive
        Long schmId,

        @DecimalMin("0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal reqAmt,

        @Min(1)
        Integer reqTenor,

        @Size(max = 1000)
        String remarks
) {
}