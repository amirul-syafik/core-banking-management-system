package com.cbms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import java.math.BigDecimal;

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

        @DecimalMin("0.0")
        @DecimalMax("100.0")
        BigDecimal interestRate,

        @Size(max = 500)
        String remarks

) {
    /** Keep historical Java callers compatible with the prior four-argument DTO. */
    public BankFixedDepositRenewalRequest(String renewalType, Long schemeId, Integer tenorMonths, String remarks) {
        this(renewalType, schemeId, tenorMonths, null, remarks);
    }
}