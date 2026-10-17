package com.cbms.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BankFinancingDisbursementRequest(

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Size(max = 100)
        String idempotencyKey,

        @NotBlank
        @Size(max = 200)
        String beneficiaryName,

        @NotBlank
        @Size(max = 100)
        String beneficiaryRef,

        @Size(max = 1000)
        String remarks
) {}