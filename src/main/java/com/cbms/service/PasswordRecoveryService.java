package com.cbms.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class PasswordRecoveryService {
    private final JdbcTemplate jdbc;

    public PasswordRecoveryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record OpenRequest(Long requestId, Long userId, String username, OffsetDateTime requestedAt) {}

    @Transactional
    public void requestAssistance(String username) {
        // Generic HTTP response for existing/unknown/disabled accounts.
        // A partial unique index prevents duplicate OPEN requests for a user.
        // Only an administrator may reset credentials after off-system identity verification.
        jdbc.update("""
                INSERT INTO public.bank_password_recovery_request (user_id)
                SELECT user_id FROM public.app_user
                WHERE username = ? AND enabled IS TRUE
                ON CONFLICT DO NOTHING
                """, username.trim());
    }

    @Transactional(readOnly = true)
    public List<OpenRequest> openRequests() {
        return jdbc.query("""
                SELECT r.request_id, r.user_id, u.username, r.requested_at
                FROM public.bank_password_recovery_request r
                JOIN public.app_user u ON u.user_id = r.user_id
                WHERE r.status = 'OPEN'
                ORDER BY r.requested_at ASC, r.request_id ASC
                LIMIT 200
                """, (rs, rowNum) -> new OpenRequest(
                rs.getLong("request_id"), rs.getLong("user_id"),
                rs.getString("username"), rs.getObject("requested_at", OffsetDateTime.class)));
    }

    public void resolveAfterAdminReset(Long userId, String adminUsername) {
        // Called within the same transaction as the actual password reset.
        jdbc.update("""
                UPDATE public.bank_password_recovery_request
                SET status='RESOLVED', resolved_by=?, resolved_at=NOW()
                WHERE user_id=? AND status='OPEN'
                """, adminUsername, userId);
    }
}
