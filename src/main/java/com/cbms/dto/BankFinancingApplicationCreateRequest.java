package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BankFinancingApplicationCreateRequest(

        @NotNull @Positive
        Long custId,

        @NotNull @Positive
        Long prodId,

        @NotNull @Positive
        Long schmId,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal reqAmt,

        @NotNull @Min(1)
        Integer reqTenor,

        @Size(max = 500)
        String financingPurpose,

        @Size(max = 1000)
        String remarks
) {}