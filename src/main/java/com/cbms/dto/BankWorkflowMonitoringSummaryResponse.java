package com.cbms.dto;

public record BankWorkflowMonitoringSummaryResponse(
        long totalInstances, long pendingInstances, long inProgressInstances,
        long approvedInstances, long rejectedInstances, long cancelledInstances,
        long openTasks, long pendingTasks, long claimedTasks, long overdueTasks,
        int overdueHours) {
}
