package com.cbms.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BankFixedDepositCreateRequest(

        @NotNull
        Long custId,

        @NotNull
        Long prodId,

        @NotNull
        Long schmId,

        @NotBlank
        @Pattern(regexp = "FIXED_DEPOSIT|TERM_DEPOSIT")
        String fdType,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal principalAmount,

        @NotNull
        @Min(1)
        Integer tenorMonths,

        @NotNull
        LocalDate placementDate,

        @Size(max = 1000)
        String remarks

) {}