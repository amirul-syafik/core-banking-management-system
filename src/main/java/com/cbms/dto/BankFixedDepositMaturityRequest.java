package com.cbms.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record BankFixedDepositMaturityRequest(

        @NotNull
        LocalDate placementDate,

        @NotNull
        @Min(1)
        @Max(600)
        Integer tenorMonths,

        @NotBlank
        @Pattern(regexp = "NONE|FOLLOWING|PRECEDING")
        String weekendConvention

) {}