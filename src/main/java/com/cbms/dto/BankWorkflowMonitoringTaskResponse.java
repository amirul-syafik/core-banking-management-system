package com.cbms.dto;

import java.time.OffsetDateTime;

public record BankWorkflowMonitoringTaskResponse(
        Long taskId, String taskRef, Long instanceId, String instanceRef,
        Long workflowId, String workflowCd, String moduleCd,
        Long entityId, String entityRef, Integer stepNo, String stepNm,
        String assignedRole, String assignedTo, String taskSts,
        OffsetDateTime assignedDt, OffsetDateTime claimedDt,
        long ageHours, boolean overdue) {
}
