package com.cbms.dto;

import java.time.LocalDate;

public record BankFixedDepositMaturityResponse(

        LocalDate placementDate,
        Integer tenorMonths,
        LocalDate contractualMaturityDate,
        LocalDate adjustedPaymentDate,
        String weekendConvention,
        Boolean monthEndPlacement,
        Boolean weekendAdjustmentApplied

) {
}