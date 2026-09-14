package com.fieldservice.service;

import com.fieldservice.dto.NotificationResponse;
import com.fieldservice.entity.*;
import com.fieldservice.exception.ResourceNotFoundException;
import com.fieldservice.mapper.EntityMapper;
import com.fieldservice.notification.NotificationGateway;
import com.fieldservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationGateway notificationGateway;

    /**
     * Creates and stores a real database notification for the recipient and dispatches push via the gateway.
     */
    @Transactional
    public NotificationEntity createNotification(UserEntity recipient, WorkEntity work,
                                                 NotificationType type, String title, String message) {
        if (recipient == null) {
            log.warn("Cannot send notification: recipient is null (type: {})", type);
            return null;
        }

        NotificationEntity entity = NotificationEntity.builder()
                .user(recipient)
                .work(work)
                .type(type)
                .title(title)
                .message(message)
                .isRead(false)
                .deliveryStatus(NotificationDeliveryStatus.STORED)
                .createdAt(Instant.now())
                .build();

        NotificationEntity saved = notificationRepository.save(entity);

        // Attempt gateway push dispatch
        try {
            Map<String, String> payload = new HashMap<>();
            if (work != null) {
                payload.put("workId", work.getId().toString());
            }
            payload.put("type", type.name());
            boolean dispatched = notificationGateway.sendPush(recipient, type, title, message, payload);
            if (dispatched) {
                saved.setDeliveryStatus(NotificationDeliveryStatus.SENT);
                saved = notificationRepository.save(saved);
            }
        } catch (Exception e) {
            log.error("Failed to dispatch push notification via gateway: {}", e.getMessage(), e);
        }

        log.info("Created notification {} for user {} (work: {}, type: {})",
                saved.getId(), recipient.getId(), work != null ? work.getId() : null, type);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(UserEntity currentUser) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(EntityMapper::toNotificationResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UserEntity currentUser) {
        return notificationRepository.countByUserIdAndIsReadFalse(currentUser.getId());
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, UserEntity currentUser) {
        NotificationEntity notification = notificationRepository.findByIdAndUserId(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }

        return EntityMapper.toNotificationResponse(notification);
    }

    @Transactional
    public void markAllAsRead(UserEntity currentUser) {
        notificationRepository.markAllAsReadForUser(currentUser.getId(), Instant.now());
    }
}
