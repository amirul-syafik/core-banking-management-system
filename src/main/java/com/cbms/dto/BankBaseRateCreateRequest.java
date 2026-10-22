package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankBaseRateCreateRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9_-]+$")
        @Size(max = 30)
        String rateCd,

        @NotBlank
        @Size(max = 150)
        String rateNm,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String ccyCd,

        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        BigDecimal ratePct,

        @NotNull
        LocalDate effDt
) {
}