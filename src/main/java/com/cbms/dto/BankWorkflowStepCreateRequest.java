package com.cbms.dto;

import jakarta.validation.constraints.*;

public record BankWorkflowStepCreateRequest(
        @NotNull @Positive Integer stepNo,
        @NotBlank @Size(max = 150) String stepNm,
        @NotBlank @Pattern(regexp = "MAKER|ADMIN") String approverRole,
        @NotNull @Positive Integer requiredApprovals
) {
}
