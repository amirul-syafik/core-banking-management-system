package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankFixedDepositAccrualResponse(
        Long accrualId,
        Long fixedDepositId,
        LocalDate accrualDate,
        String dayCountBasis,
        BigDecimal dailyInterest,
        BigDecimal accruedInterest,
        BigDecimal cumulativeInterest,
        String postingReference,
        String status
) {}