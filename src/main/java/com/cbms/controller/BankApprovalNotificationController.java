package com.cbms.controller;

import com.cbms.service.BankApprovalNotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class BankApprovalNotificationController {
    private final BankApprovalNotificationService notifications;

    public BankApprovalNotificationController(BankApprovalNotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<Map<String, Object>> mine(Authentication user,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "50") int limit) {
        return notifications.mine(user, unreadOnly, limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication user) {
        return Map.of("unreadCount", notifications.unreadCount(user));
    }

    @PostMapping("/{notificationId}/read")
    public Map<String, Boolean> markRead(@PathVariable long notificationId, Authentication user) {
        notifications.markRead(notificationId, user);
        return Map.of("read", true);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(Authentication user) {
        return Map.of("updated", notifications.markAllRead(user));
    }
}
