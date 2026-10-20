package com.cbms.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;

/**
 * Audit actor comes from the validated Spring Security principal, never a request body.
 */
public final class CurrentActor {
    private CurrentActor() {
    }

    public static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || auth.getName() == null || auth.getName().isBlank()) {
            throw new AccessDeniedException("Authenticated user required for audited changes");
        }
        return auth.getName();
    }
}
