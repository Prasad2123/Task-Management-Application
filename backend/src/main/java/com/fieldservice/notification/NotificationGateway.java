package com.fieldservice.notification;

import com.fieldservice.entity.NotificationType;
import com.fieldservice.entity.UserEntity;

import java.util.Map;

/**
 * Gateway abstraction for external push notification dispatch (e.g. Firebase Cloud Messaging).
 * Designed for production FCM integration in Prompt 12 without changing domain notification services.
 */
public interface NotificationGateway {

    /**
     * Attempts to deliver a push notification to the user's registered device.
     * Returns true if successfully handed off to the push transport.
     */
    boolean sendPush(UserEntity recipient, NotificationType type, String title, String message, Map<String, String> data);
}
