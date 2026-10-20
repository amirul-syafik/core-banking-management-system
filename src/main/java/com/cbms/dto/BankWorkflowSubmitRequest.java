package com.cbms.dto;

import jakarta.validation.constraints.*;

public record BankWorkflowSubmitRequest(
        @NotNull @Positive Long workflowId,
        @NotNull @Positive Long entityId,
        @Size(max = 100) String entityRef,
        @Size(max = 1000) String remarks
) {
}
