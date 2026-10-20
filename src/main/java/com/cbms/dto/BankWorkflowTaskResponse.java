package com.cbms.dto;

import java.time.OffsetDateTime;

public record BankWorkflowTaskResponse(
        Long taskId, String taskRef, Long instanceId, String instanceRef,
        Long workflowId, String workflowCd, Long entityId, String entityRef,
        Long stepId, Integer stepNo, String stepNm, String assignedRole,
        String assignedTo, String taskSts, OffsetDateTime assignedDt,
        OffsetDateTime claimedDt, String actionBy, OffsetDateTime actionDt,
        String actionRemarks, OffsetDateTime crtDt, String crtBy,
        OffsetDateTime updDt, String updBy
) {
}
