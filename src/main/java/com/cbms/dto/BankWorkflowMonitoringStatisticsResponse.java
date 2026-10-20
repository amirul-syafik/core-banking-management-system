package com.cbms.dto;

public record BankWorkflowMonitoringStatisticsResponse(
        long completedInstances, long approvedInstances, long rejectedInstances,
        long totalDecidedTasks, long approvedTasks, long rejectedTasks,
        Double averageCompletionHours) {
}
