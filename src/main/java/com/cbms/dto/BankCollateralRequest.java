package com.cbms.dto;

import jakarta.validation.constraints.*;

public record BankCollateralRequest(
        @NotBlank @Size(max = 30) String collateralTyp,
        @NotBlank @Size(max = 500) String collateralDesc,
        @NotNull @Positive Long ownerCustId,
        @Positive Long vehicleId
) {
}
