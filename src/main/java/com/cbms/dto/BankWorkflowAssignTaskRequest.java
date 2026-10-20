package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankWorkflowAssignTaskRequest(
        @NotBlank @Size(max = 100) String username,
        @Size(max = 1000) String remarks
) {}
