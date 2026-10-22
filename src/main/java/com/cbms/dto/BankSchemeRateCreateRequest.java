package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankSchemeRateCreateRequest(
        @NotNull
        Long schmId,

        @NotBlank
        @Pattern(regexp = "FIXED|FLOATING")
        String rateTyp,

        String baseRateCd,

        BigDecimal fixedRatePct,

        BigDecimal marginPct,

        @NotNull
        LocalDate effDt
) {
}