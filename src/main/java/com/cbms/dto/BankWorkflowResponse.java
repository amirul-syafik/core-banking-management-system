package com.cbms.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BankWorkflowResponse(Long workflowId, String workflowCd, String workflowNm, String workflowDesc,
                                   String moduleCd, String entityTyp, String workflowSts, Integer versionNo,
                                   OffsetDateTime crtDt, String crtBy, OffsetDateTime updDt, String updBy,
                                   List<BankWorkflowStepResponse> steps) {
}
