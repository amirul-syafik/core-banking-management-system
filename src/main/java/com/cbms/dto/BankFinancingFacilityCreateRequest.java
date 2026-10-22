package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BankFinancingFacilityCreateRequest(

        @NotNull @Positive Long custId,

        @NotNull @Positive Long prodId,

        @NotNull @Positive Long schmId,

        @NotBlank
        @Pattern(regexp =
                "HIRE_PURCHASE|PERSONAL_FINANCING|TERM_LOAN|CREDIT_LINE|FLOOR_STOCKING")
        String facTyp,

        @NotNull @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal apprAmt,

        @NotNull @Min(1)
        Integer tenorMth,

        @Size(max = 1000)
        String remarks
) {
}