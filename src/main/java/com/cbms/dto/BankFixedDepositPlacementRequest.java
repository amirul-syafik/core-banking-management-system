package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BankFixedDepositPlacementRequest(

        @NotNull
        Long sourceAccountId,

        // Optional legacy idempotency token; it no longer selects the generated business reference.
        @Size(max = 50)
        String placementReference,

        @Size(max = 1000)
        String remarks

) {
}
