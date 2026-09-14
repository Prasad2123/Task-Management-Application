package com.fieldservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long userId;
    private Long workId;
    private String workTitle;
    private String type;
    private String title;
    private String message;
    private Boolean isRead;
    private Instant readAt;
    private String deliveryStatus;
    private Instant createdAt;
}
