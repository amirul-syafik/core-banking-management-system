package com.cbms.dto;

import java.time.OffsetDateTime;

public record CustomerStatusHistoryRow(String cifNo, String customerName, String fromStatus,
                                       String toStatus, String requestStatus, String reason,
                                       OffsetDateTime requestedAt, OffsetDateTime reviewedAt) {
}
