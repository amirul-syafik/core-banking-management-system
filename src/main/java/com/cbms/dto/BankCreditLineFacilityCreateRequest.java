package com.cbms.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BankCreditLineFacilityCreateRequest(

        @NotNull Long custId,
        @NotNull Long prodId,
        @NotNull Long schmId,

        @NotBlank String clFacTyp,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal apprLimit,

        @NotNull LocalDate effDt,
        @NotNull LocalDate expiryDt,

        @NotBlank String rateTyp,

        @NotNull
        @DecimalMin("0.0")
        BigDecimal effRate,

        @Size(max = 1000)
        String remarks
) {}