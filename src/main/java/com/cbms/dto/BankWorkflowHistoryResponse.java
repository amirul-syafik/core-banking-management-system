package com.cbms.dto;

import java.time.OffsetDateTime;

public record BankWorkflowHistoryResponse(
        Long histId,
        Long instanceId,
        Long taskId,
        String actionCd,
        String fromSts,
        String toSts,
        String actionRemarks,
        String actionBy,
        OffsetDateTime actionDt
) {
}
