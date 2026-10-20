package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;

public record BankCollateralStatusRequest(@NotBlank String collateralSts) {
}
