package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BankProductStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|INACTIVE")
        String status
) {
}