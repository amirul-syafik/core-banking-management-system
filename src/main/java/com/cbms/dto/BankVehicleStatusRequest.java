package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;

public record BankVehicleStatusRequest(@NotBlank String vehicleSts) {
}
