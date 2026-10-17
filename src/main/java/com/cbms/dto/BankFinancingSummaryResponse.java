package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankFinancingSummaryResponse(
        Long facId,
        String facRef,
        String facSts,

        Long applId,
        Long custId,
        Long prodId,
        Long schmId,

        String facTyp,
        String bankingTyp,
        String ccyCd,

        BigDecimal approvedAmount,
        Integer tenorMonths,
        String rateType,
        BigDecimal effectiveRate,

        LocalDate approvalDate,
        LocalDate bookingDate,
        LocalDate maturityDate,

        String disbursementStatus,
        BigDecimal disbursementAmount,

        BankFinancingOutstandingResponse outstanding
) {}
