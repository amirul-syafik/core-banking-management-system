package com.cbms.dto;

import jakarta.validation.constraints.*;

public record SystemParameterRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_.-]{1,99}") String key,
        @NotBlank @Size(max = 500) String value,
        @NotBlank @Pattern(regexp = "STRING|INTEGER|DECIMAL|BOOLEAN") String valueType,
        @Size(max = 500) String description) {
}
