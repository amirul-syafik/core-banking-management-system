package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankFinancingRepriceResponse(
        Long facId, LocalDate reviewDate, BigDecimal previousRate,
        BigDecimal currentRate, int updatedInstalments,
        BigDecimal previousFutureInterest, BigDecimal revisedFutureInterest
) {}
