package com.cbms.dto;

import java.time.OffsetDateTime;

public record BankWorkflowAuditResponse(
        Long histId,
        Long instanceId,
        String instanceRef,
        Long workflowId,
        String workflowCd,
        Long entityId,
        String entityRef,
        Long taskId,
        String actionCd,
        String fromSts,
        String toSts,
        String actionRemarks,
        String actionBy,
        OffsetDateTime actionDt
) {
}
