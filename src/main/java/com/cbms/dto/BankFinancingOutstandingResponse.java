package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankFinancingOutstandingResponse(
        Long facId,
        String facRef,
        String facSts,
        String ccyCd,

        BigDecimal approvedAmount,
        BigDecimal disbursedPrincipal,
        BigDecimal principalPaid,
        BigDecimal principalOutstanding,

        BigDecimal totalScheduledInterest,
        BigDecimal interestPaid,
        BigDecimal remainingScheduledInterest,

        BigDecimal totalScheduledRepayment,
        BigDecimal totalPaid,
        BigDecimal totalUnpaid,

        BigDecimal overdueAmount,
        BigDecimal overduePrincipal,
        BigDecimal overdueInterest,

        Integer totalInstalments,
        Integer paidInstalments,
        Integer partialInstalments,
        Integer pendingInstalments,
        Integer overdueInstalments,

        Integer nextInstalmentNo,
        LocalDate nextDueDate,
        BigDecimal nextDueAmount,

        BigDecimal repaymentProgressPct,
        LocalDate maturityDate
) {}
