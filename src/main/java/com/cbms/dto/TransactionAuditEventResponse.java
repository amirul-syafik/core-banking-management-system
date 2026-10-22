package com.cbms.dto;

import java.time.OffsetDateTime;

public record TransactionAuditEventResponse(
        String eventKey,
        String reference,
        Long accountId,
        String action,
        String actor,
        OffsetDateTime eventAt,
        String description,
        String relatedReference
) {
}
