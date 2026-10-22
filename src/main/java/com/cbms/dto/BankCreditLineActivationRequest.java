package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankCreditLineActivationRequest(
        @NotBlank
        @Size(max = 1000)
        String reason
) {
}