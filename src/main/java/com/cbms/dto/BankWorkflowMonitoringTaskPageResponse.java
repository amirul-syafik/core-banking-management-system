package com.cbms.dto;

import java.util.List;

public record BankWorkflowMonitoringTaskPageResponse(
        List<BankWorkflowMonitoringTaskResponse> content,
        long totalElements, int page, int size, long totalPages) {
}
