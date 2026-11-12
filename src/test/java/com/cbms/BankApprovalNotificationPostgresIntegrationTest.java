package com.cbms;

import com.cbms.service.BankApprovalNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BankApprovalNotificationPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BankApprovalNotificationService notifications;

    private String enabledUser() {
        return jdbc.queryForObject("""
            SELECT username FROM public.app_user WHERE enabled = TRUE ORDER BY user_id LIMIT 1
            """, String.class);
    }

    private Authentication actor(String username) {
        return new UsernamePasswordAuthenticationToken(username, "N/A", List.of());
    }

    @Test void notificationCanBeReadOnlyByItsRecipient() {
        String recipient = enabledUser();
        long sourceId = -Math.abs(System.nanoTime());
        notifications.notifyUser(recipient, "FIXED_DEPOSITS", "FD_APPROVAL", sourceId,
                "DECIDED", "Review completed", "The request has been approved");
        List<Map<String, Object>> stored = jdbc.queryForList("""
            SELECT notification_id, recipient_username FROM public.bank_user_notification
            WHERE source_type = 'FD_APPROVAL' AND source_id = ? AND event_cd = 'DECIDED'
            """, sourceId);
        assertEquals(1, stored.size());
        long notificationId = ((Number) stored.get(0).get("notification_id")).longValue();
        assertThrows(ResponseStatusException.class,
                () -> notifications.markRead(notificationId, actor("NOT_THE_RECIPIENT")));
        notifications.markRead(notificationId, actor(recipient));
        Integer isRead = jdbc.queryForObject("""
            SELECT COUNT(*) FROM public.bank_user_notification
            WHERE notification_id = ? AND read_at IS NOT NULL
            """, Integer.class, notificationId);
        assertEquals(1, isRead);
    }

    @Test void repeatedEventCreatesOnlyOneNotification() {
        String recipient = enabledUser();
        long sourceId = -Math.abs(System.nanoTime());
        for (int attempt = 0; attempt < 2; attempt++) {
            notifications.notifyUser(recipient, "FINANCING", "FINANCING_APPLICATION", sourceId,
                    "PENDING", "Financing review", "Review required");
        }
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM public.bank_user_notification
            WHERE source_type = 'FINANCING_APPLICATION' AND source_id = ? AND event_cd = 'PENDING'
            """, Integer.class, sourceId);
        assertEquals(1, count);
    }
}
