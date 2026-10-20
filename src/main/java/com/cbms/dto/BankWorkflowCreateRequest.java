package com.cbms.dto;

import jakarta.validation.constraints.*;

public record BankWorkflowCreateRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Z0-9_-]+$") String workflowCd,
        @NotBlank @Size(max = 150) String workflowNm,
        @Size(max = 500) String workflowDesc,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Z0-9_-]+$") String moduleCd,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Z0-9_-]+$") String entityTyp
) {
}
