package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BankFloorStockCreateRequest(
        @NotBlank @Size(max = 50)
        String vinNo,

        @NotBlank @Size(max = 100)
        String vehicleMake,

        @NotBlank @Size(max = 100)
        String vehicleModel,

        @NotNull @Min(1900) @Max(2100)
        Integer manufactureYear,

        @NotNull @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal purchasePrice,

        @NotNull @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal financedAmount,

        @Size(max = 1000)
        String remarks
) {
}