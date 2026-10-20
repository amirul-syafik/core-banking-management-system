package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankFinancingCollateralReleaseRequest(@NotBlank @Size(max = 500) String reason) {
}
