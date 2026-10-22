package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BankFinancingApprovalRequest(

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal apprAmt,

        @NotNull
        @Min(1)
        Integer apprTenor,

        @NotNull
        @DecimalMin("0.000000")
        @DecimalMax("100.000000")
        @Digits(integer = 3, fraction = 6)
        BigDecimal apprRate,

        @Size(max = 1000)
        String remarks
) {
}