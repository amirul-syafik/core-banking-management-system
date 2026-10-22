package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BankSchemeCreateRequest(

        @NotNull
        Long prodId,

        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[A-Z0-9_-]+$")
        String schmCd,

        @NotBlank
        @Size(max = 150)
        String schmNm,

        @Size(max = 500)
        String schmDesc,

        @DecimalMin("0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal minAmt,

        @DecimalMin("0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal maxAmt,

        @Min(1)
        Integer minTenor,

        @Min(1)
        Integer maxTenor,

        @Size(max = 1000)
        String eligibilityDesc

) {
}
