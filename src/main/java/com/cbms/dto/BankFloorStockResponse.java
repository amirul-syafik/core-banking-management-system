package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankFloorStockResponse(
        Long stockId,
        String stockReference,
        Long facilityId,
        String vinNo,
        String vehicleMake,
        String vehicleModel,
        Integer manufactureYear,
        BigDecimal purchasePrice,
        BigDecimal financedAmount,
        BigDecimal outstandingAmount,
        String status,
        LocalDate financeDate,
        LocalDate settlementDate,
        String remarks
) {}