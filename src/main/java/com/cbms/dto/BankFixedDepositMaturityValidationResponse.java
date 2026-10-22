package com.cbms.dto;

import java.time.LocalDate;

public record BankFixedDepositMaturityValidationResponse(

        Long fixedDepositId,
        String fixedDepositReference,
        String status,
        LocalDate placementDate,
        Integer tenorMonths,
        LocalDate storedMaturityDate,
        LocalDate calculatedMaturityDate,
        Boolean valid,
        String message

) {
}