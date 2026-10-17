package com.cbms.dto;

import java.util.List;

public record BankFinancingApplicationValidationResponse(
        Long applId,
        String applRef,
        boolean valid,
        List<String> errors
) {}