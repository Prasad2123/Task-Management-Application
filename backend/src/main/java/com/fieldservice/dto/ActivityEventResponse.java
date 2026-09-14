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
public class ActivityEventResponse {
    private Long id;
    private Long workId;
    private String eventType;
    private String description;
    private WorkResponse.UserSummary performedBy;
    private Instant eventTimestamp;
    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;
    private Instant createdAt;
}
