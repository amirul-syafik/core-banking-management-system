package com.cbms.dto;

import java.time.OffsetDateTime;
import java.util.Set;

public record AdminUserResponse(Long userId, String username, boolean enabled, Set<String> roles,
                                OffsetDateTime createdAt) {
}
