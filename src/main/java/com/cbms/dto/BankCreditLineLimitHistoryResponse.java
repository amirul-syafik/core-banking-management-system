package com.cbms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BankCreditLineLimitHistoryResponse(
        Long histId,
        Long clFacId,
        String actionType,
        BigDecimal oldLimit,
        BigDecimal newLimit,
        BigDecimal utilizedAmount,
        String reason,
        OffsetDateTime actionDate,
        String actionBy
) {}