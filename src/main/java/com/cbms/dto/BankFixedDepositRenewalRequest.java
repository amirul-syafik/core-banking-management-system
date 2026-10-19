package com.cbms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BankFixedDepositRenewalRequest(

        @NotNull
        @Pattern(regexp = "PRINCIPAL_ONLY|PRINCIPAL_PLUS_INTEREST")
        String renewalType,

        @NotNull
        Long schemeId,

        @NotNull
        @Min(1)
        @Max(600)
        Integer tenorMonths,

        @Size(max = 500)
        String remarks

) {
}