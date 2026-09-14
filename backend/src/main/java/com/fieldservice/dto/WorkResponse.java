package com.fieldservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkResponse {
    private Long id;
    private String title;
    private String workType;
    private String description;
    private String notes;
    private LocalDate scheduledDate;
    private String status;
    private Boolean readyForCompletion;

    // People (no sensitive data)
    private UserSummary serviceBoy;
    private UserSummary poc;
    private UserSummary supervisor;

    // Location & Geofence
    private String companyName;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double allowedRadiusMeters;
    private Double distanceFromWorkMeters;
    private Boolean locationVerified;

    // Lifecycle timestamps
    private Instant startTime;
    private Instant submittedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummary {
        private Long id;
        private String name;
        private String email;
        private String phone;
        private String role;
    }
}
