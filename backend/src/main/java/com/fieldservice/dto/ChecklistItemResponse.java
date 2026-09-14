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
public class ChecklistItemResponse {
    private Long id;
    private Long workId;
    private String title;
    private String description;
    private boolean additional;
    private boolean completed;
    private Instant completedAt;
    private WorkResponse.UserSummary completedBy;
    private int displayOrder;
    private Instant createdAt;
    private Instant updatedAt;
}
