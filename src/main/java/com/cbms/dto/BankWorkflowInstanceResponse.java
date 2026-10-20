package com.cbms.dto;

import java.time.OffsetDateTime;

public record BankWorkflowInstanceResponse(
        Long instanceId,
        String instanceRef,
        Long workflowId,
        String workflowCd,
        Integer workflowVersion,
        String moduleCd,
        String entityTyp,
        Long entityId,
        String entityRef,
        String workflowSts,
        Integer currentStepNo,
        String submittedBy,
        OffsetDateTime submittedDt,
        String completedBy,
        OffsetDateTime completedDt,
        String remarks,
        OffsetDateTime crtDt,
        String crtBy,
        OffsetDateTime updDt,
        String updBy
) {
}
