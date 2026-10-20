package com.cbms.dto;

import jakarta.validation.constraints.*;

public record SystemParameterUpdateRequest(@NotBlank @Size(max = 500) String value,
                                           @Size(max = 500) String description) {
}
