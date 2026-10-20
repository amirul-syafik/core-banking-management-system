package com.cbms.dto;

import java.time.OffsetDateTime;

public record BankWorkflowStepResponse(Long stepId, Long workflowId, Integer stepNo, String stepNm, String approverRole,
                                       Integer requiredApprovals, String stepSts, OffsetDateTime crtDt, String crtBy) {
}
