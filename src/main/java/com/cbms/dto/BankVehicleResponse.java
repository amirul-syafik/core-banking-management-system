package com.cbms.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BankVehicleResponse(
        Long vehicleId, Long ownerCustId, String ownerCustNm,
        String registrationNo, LocalDate registrationDt,
        String chassisNo, String engineNo, String vehicleMake,
        String vehicleModel, String vehicleVariant, Integer manufactureYear,
        String vehicleType, String vehicleCondition, String colour,
        Integer engineCapacityCc, String vehicleSts,
        OffsetDateTime crtDt, String crtBy, OffsetDateTime updDt, String updBy
) {
}
