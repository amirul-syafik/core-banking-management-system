package com.cbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerStatusProposal(
        @NotBlank @Pattern(regexp = "PENDING|ACTIVE|INACTIVE|BLOCKED", flags = Pattern.Flag.CASE_INSENSITIVE) String status,
        @NotBlank @Size(max = 500) String reason) {
}
