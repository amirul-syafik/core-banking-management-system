package com.cbms.dto;

import java.math.BigDecimal;

public record BankFinancingRepaymentAllocationResponse(
        Long schedId,
        Integer instNo,
        BigDecimal principalAmt,
        BigDecimal interestAmt
) {
}