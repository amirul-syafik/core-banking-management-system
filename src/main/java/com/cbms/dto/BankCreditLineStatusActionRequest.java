package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankCreditLineStatusActionRequest(
        @NotBlank
        @Size(max = 1000)
        String reason
) {}