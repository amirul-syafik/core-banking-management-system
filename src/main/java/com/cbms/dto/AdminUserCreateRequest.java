package com.cbms.dto;

import jakarta.validation.constraints.*;

import java.util.Set;

public record AdminUserCreateRequest(@NotBlank @Size(max = 100) String username,
                                     @NotBlank @Size(min = 12, max = 128) String password,
                                     @NotEmpty Set<@Pattern(regexp = "VIEWER|MAKER|ADMIN") String> roles) {
}
