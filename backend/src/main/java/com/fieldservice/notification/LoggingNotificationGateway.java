package com.fieldservice.notification;

import com.fieldservice.entity.NotificationType;
import com.fieldservice.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Standard notification gateway used when FCM credentials are not yet configured.
 * Logs push notification delivery attempts and serves as the bridge for future FCM credentials.
 */
@Component
@Slf4j
public class LoggingNotificationGateway implements NotificationGateway {

    @Override
    public boolean sendPush(UserEntity recipient, NotificationType type, String title, String message, Map<String, String> data) {
        log.info("[PUSH NOTIFICATION GATEWAY] Dispatched push alert to user {} (role: {}): \"{}\" - \"{}\" (type: {})",
                recipient.getId(), recipient.getRole(), title, message, type);
        return true;
    }
}
