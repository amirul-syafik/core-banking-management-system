package com.cbms.service;

import java.util.Locale;

/**
 * Shared request validation for paginated read-only API endpoints.
 */
public final class RequestFilterValidation {
    private RequestFilterValidation() {
    }

    public static void pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and 100");
        }
    }

    /**
     * Blank optional filters are treated as absent; nonblank values are normalized.
     */
    public static String optionalUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
