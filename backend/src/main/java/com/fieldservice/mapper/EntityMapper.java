package com.fieldservice.mapper;

import com.fieldservice.dto.*;
import com.fieldservice.entity.*;

/**
 * Static mapper methods converting JPA entities to API response DTOs.
 * Ensures sensitive fields (passwordHash) are never included in responses.
 */
public class EntityMapper {

    private EntityMapper() {}

    public static WorkResponse.UserSummary toUserSummary(UserEntity user) {
        if (user == null) return null;
        return WorkResponse.UserSummary.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .build();
    }

    public static WorkResponse toWorkResponse(WorkEntity work) {
        return WorkResponse.builder()
                .id(work.getId())
                .title(work.getTitle())
                .workType(work.getWorkType())
                .description(work.getDescription())
                .notes(work.getNotes())
                .scheduledDate(work.getScheduledDate())
                .status(work.getStatus().name())
                .serviceBoy(toUserSummary(work.getServiceBoy()))
                .poc(toUserSummary(work.getPoc()))
                .supervisor(toUserSummary(work.getSupervisor()))
                .companyName(work.getCompanyName())
                .address(work.getAddress())
                .latitude(work.getLatitude())
                .longitude(work.getLongitude())
                .allowedRadiusMeters(work.getAllowedRadiusMeters() != null ? work.getAllowedRadiusMeters() : 150.0)
                .startTime(work.getStartTime())
                .submittedAt(work.getSubmittedAt())
                .completedAt(work.getCompletedAt())
                .readyForCompletion(work.getStatus() == WorkStatus.SUPERVISOR_APPROVED)
                .createdAt(work.getCreatedAt())
                .updatedAt(work.getUpdatedAt())
                .build();
    }

    public static ChecklistItemResponse toChecklistResponse(ChecklistItemEntity item) {
        return ChecklistItemResponse.builder()
                .id(item.getId())
                .workId(item.getWork().getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .additional(item.isAdditional())
                .completed(item.isCompleted())
                .completedAt(item.getCompletedAt())
                .completedBy(toUserSummary(item.getCompletedBy()))
                .displayOrder(item.getDisplayOrder())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public static AdditionalWorkResponse toAdditionalWorkResponse(AdditionalWorkEntity aw) {
        return AdditionalWorkResponse.builder()
                .id(aw.getId())
                .workId(aw.getWork().getId())
                .description(aw.getDescription())
                .clientItemId(aw.getClientItemId())
                .createdBy(toUserSummary(aw.getCreatedBy()))
                .createdAt(aw.getCreatedAt())
                .updatedAt(aw.getUpdatedAt())
                .build();
    }

    public static ApprovalResponse toApprovalResponse(ApprovalEntity approval) {
        return ApprovalResponse.builder()
                .id(approval.getId())
                .workId(approval.getWork().getId())
                .approver(toUserSummary(approval.getApprover()))
                .approverRole(approval.getApproverRole().name())
                .status(approval.getStatus().name())
                .decidedAt(approval.getDecidedAt())
                .rejectionReason(approval.getRejectionReason())
                .createdAt(approval.getCreatedAt())
                .updatedAt(approval.getUpdatedAt())
                .build();
    }

    public static ActivityEventResponse toActivityEventResponse(ActivityEventEntity event) {
        return ActivityEventResponse.builder()
                .id(event.getId())
                .workId(event.getWork().getId())
                .eventType(event.getEventType().name())
                .description(event.getDescription())
                .performedBy(toUserSummary(event.getPerformedBy()))
                .eventTimestamp(event.getEventTimestamp())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .accuracyMeters(event.getAccuracyMeters())
                .createdAt(event.getCreatedAt())
                .build();
    }

    public static WorkPhotoResponse toWorkPhotoResponse(WorkPhotoEntity photo) {
        return WorkPhotoResponse.builder()
                .id(photo.getId())
                .workId(photo.getWork().getId())
                .title(photo.getTitle())
                .category(photo.getCategory().name())
                .caption(photo.getCaption())
                .uploadedBy(toUserSummary(photo.getUploadedBy()))
                .storageReference(photo.getStorageReference())
                .uploadStatus(photo.getUploadStatus().name())
                .fileName(photo.getFileName())
                .contentType(photo.getContentType())
                .fileSize(photo.getFileSize())
                .photoUrl(photo.getPhotoUrl() != null ? photo.getPhotoUrl() : "/api/works/" + photo.getWork().getId() + "/photos/" + photo.getId())
                .clientPhotoId(photo.getClientPhotoId())
                .createdAt(photo.getCreatedAt())
                .updatedAt(photo.getUpdatedAt())
                .build();
    }

    public static NotificationResponse toNotificationResponse(NotificationEntity notification) {
        if (notification == null) return null;
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUser().getId())
                .workId(notification.getWork() != null ? notification.getWork().getId() : null)
                .workTitle(notification.getWork() != null ? notification.getWork().getTitle() : null)
                .type(notification.getType() != null ? notification.getType().name() : null)
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .deliveryStatus(notification.getDeliveryStatus() != null ? notification.getDeliveryStatus().name() : null)
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
