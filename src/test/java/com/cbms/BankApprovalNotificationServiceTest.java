package com.cbms;

import com.cbms.service.BankApprovalNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankApprovalNotificationServiceTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    BankApprovalNotificationService notifications = new BankApprovalNotificationService(jdbc);
    private static Authentication user(String username) {
        return new UsernamePasswordAuthenticationToken(username, "N/A", List.of());
    }

    @Test void roleNotificationsExcludeMaker() {
        notifications.notifyRole("ADMIN", "MAKER01", "FIXED_DEPOSITS", "FD_APPROVAL", 4L,
                "PENDING", "Request", "Review required");
        verify(jdbc).update(contains("JOIN public.app_user_role"),
                eq("FIXED_DEPOSITS"), eq("FD_APPROVAL"), eq(4L), eq("PENDING"),
                eq("Request"), eq("Review required"), eq("ADMIN"), eq("MAKER01"));
    }

    @Test void unreadNotificationsAreScopedToAuthenticatedUserAndCapped() {
        when(jdbc.queryForList(anyString(), eq("CHECKER01"), eq(true), eq(100)))
                .thenReturn(List.of(Map.of("notificationId", 10L)));
        var data = notifications.mine(user("CHECKER01"), true, 999);
        assertEquals(1, data.size());
        assertEquals(10L, data.get(0).get("notificationId"));
    }

    @Test void onlyOwnerMayMarkRead() {
        when(jdbc.update(contains("WHERE notification_id"), eq(22L), eq("CHECKER01")))
                .thenReturn(0);
        var error = assertThrows(ResponseStatusException.class,
                () -> notifications.markRead(22, user("CHECKER01")));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test void invalidNotificationIdDoesNotAccessDatabase() {
        var error = assertThrows(ResponseStatusException.class,
                () -> notifications.markRead(0, user("CHECKER01")));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test void unauthenticatedUserCannotReadNotifications() {
        var error = assertThrows(ResponseStatusException.class,
                () -> notifications.mine(null, false, 10));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test void unreadCountReturnsZeroForEmptyAccount() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("MAKER01"))).thenReturn(0L);
        assertEquals(0L, notifications.unreadCount(user("MAKER01")));
    }
}
