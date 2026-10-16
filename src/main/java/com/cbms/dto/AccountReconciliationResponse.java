package com.cbms.dto;

import java.math.BigDecimal;

public record AccountReconciliationResponse(
        Long acctId,
        String acctNo,
        String ccyCd,
        BigDecimal storedBalance,
        BigDecimal calculatedBalance,
        BigDecimal difference,
        boolean reconciled
) {
}