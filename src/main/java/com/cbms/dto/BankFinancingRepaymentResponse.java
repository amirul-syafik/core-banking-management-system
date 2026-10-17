package com.cbms.dto;

import java.math.BigDecimal;
import java.util.List;

public record BankFinancingRepaymentResponse(
        Long repayId,
        Long facId,
        String repayRef,
        BigDecimal repayAmt,
        String repaySts,
        String paymentRef,
        String paymentChannel,
        List<BankFinancingRepaymentAllocationResponse> allocations
) {}