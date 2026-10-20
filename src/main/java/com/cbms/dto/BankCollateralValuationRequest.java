package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankCollateralValuationRequest(
        @NotNull LocalDate valuationDt,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal marketValue,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal forcedSaleValue,
        @NotBlank @Size(max = 150) String valuationSource,
        @Size(max = 500) String remarks
) {
}
