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
public class AdditionalWorkResponse {
    private Long id;
    private Long workId;
    private String description;
    private String clientItemId;
    private WorkResponse.UserSummary createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
