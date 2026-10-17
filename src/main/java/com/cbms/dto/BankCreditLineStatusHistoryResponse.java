package com.cbms.dto;

import java.time.OffsetDateTime;

public record BankCreditLineStatusHistoryResponse(
        Long historyId,
        Long facilityId,
        String actionType,
        String oldStatus,
        String newStatus,
        String reason,
        OffsetDateTime actionDate,
        String actionBy
) {}