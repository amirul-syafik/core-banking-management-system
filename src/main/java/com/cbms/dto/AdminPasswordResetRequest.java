package com.cbms.dto;

import jakarta.validation.constraints.*;

public record AdminPasswordResetRequest(@NotBlank @Size(min = 12, max = 128) String newPassword) {
}
