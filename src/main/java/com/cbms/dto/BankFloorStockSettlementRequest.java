package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankFloorStockSettlementRequest(
        @NotBlank @Size(max = 50)
        String settlementReference,

        @NotBlank @Size(max = 1000)
        String reason
) {}