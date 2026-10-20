package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record BankVehicleRequest(
        @NotNull @Positive Long ownerCustId,
        @Size(max = 30) String registrationNo,
        LocalDate registrationDt,
        @NotBlank @Size(max = 100) String chassisNo,
        @Size(max = 100) String engineNo,
        @NotBlank @Size(max = 100) String vehicleMake,
        @NotBlank @Size(max = 100) String vehicleModel,
        @Size(max = 100) String vehicleVariant,
        @NotNull @Min(1900) @Max(2100) Integer manufactureYear,
        @NotBlank String vehicleType,
        @NotBlank String vehicleCondition,
        @Size(max = 50) String colour,
        @Positive Integer engineCapacityCc
) {
}
