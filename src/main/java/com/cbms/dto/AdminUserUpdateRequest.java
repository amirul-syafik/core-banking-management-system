package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.util.Set;

public record AdminUserUpdateRequest(@NotEmpty Set<@Pattern(regexp = "VIEWER|MAKER|ADMIN") String> roles) {
}
