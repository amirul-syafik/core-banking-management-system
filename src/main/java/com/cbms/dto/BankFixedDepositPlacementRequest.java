package com.cbms.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BankFixedDepositPlacementRequest(

        @NotNull
        Long sourceAccountId,

        @NotBlank
        @Size(max = 50)
        String placementReference,

        @Size(max = 1000)
        String remarks

) {}