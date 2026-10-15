package com.cbms.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
