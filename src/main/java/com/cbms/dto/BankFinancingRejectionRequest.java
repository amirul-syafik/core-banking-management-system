package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankFinancingRejectionRequest(

        @NotBlank
        @Size(max = 1000)
        String remarks
) {
}