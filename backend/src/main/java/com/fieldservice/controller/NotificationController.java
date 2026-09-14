package com.fieldservice.controller;

import com.fieldservice.dto.NotificationResponse;
import com.fieldservice.entity.UserEntity;
import com.fieldservice.security.SecurityUtils;
import com.fieldservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /api/notifications
     * Lists all notifications for the authenticated user, newest first.
     */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getMyNotifications() {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(notificationService.getMyNotifications(currentUser));
    }

    /**
     * GET /api/notifications/unread-count
     * Returns the count of unread notifications for the authenticated user.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        long count = notificationService.getUnreadCount(currentUser);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    /**
     * PATCH /api/notifications/{id}/read
     * Marks a specific notification as read.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(notificationService.markAsRead(id, currentUser));
    }

    /**
     * POST /api/notifications/read-all
     * Marks all notifications as read for the authenticated user.
     */
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead() {
        UserEntity currentUser = SecurityUtils.getCurrentUser();
        notificationService.markAllAsRead(currentUser);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }
}
