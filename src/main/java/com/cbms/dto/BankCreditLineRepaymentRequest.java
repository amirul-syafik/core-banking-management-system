package com.cbms.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BankCreditLineRepaymentRequest(

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal repayAmount,

        @NotBlank
        @Size(max = 50)
        String repayReference,

        @Size(max = 1000)
        String remarks

) {}