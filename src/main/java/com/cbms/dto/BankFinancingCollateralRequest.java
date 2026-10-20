package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BankFinancingCollateralRequest(@NotNull @Positive Long applId, @NotNull @Positive Long collateralId,
                                             @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal allocatedAmount,
                                             @Size(max = 500) String remarks) {
}
