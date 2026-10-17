package com.cbms.dto;

import jakarta.validation.constraints.Size;

public record BankFinancingFacilityClosureRequest(
        @Size(max = 1000)
        String remarks
) {}