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

        @DecimalMin("0.0")
        @DecimalMax("100.0")
        BigDecimal reqRate,

        @Size(max = 500)
        String financingPurpose,

        @Size(max = 1000)
        String remarks
 ) {
    /** Compatibility with existing tests and clients constructed inside Java. */
    public BankFinancingApplicationCreateRequest(Long custId, Long prodId, Long schmId,
                                                 BigDecimal reqAmt, Integer reqTenor,
                                                 String financingPurpose, String remarks) {
        this(custId, prodId, schmId, reqAmt, reqTenor, null, financingPurpose, remarks);
    }
}