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
public class WorkPhotoResponse {
    private Long id;
    private Long workId;
    private String title;
    private String category;
    private String caption;
    private WorkResponse.UserSummary uploadedBy;
    private String storageReference;
    private String uploadStatus;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String photoUrl;
    private String clientPhotoId;
    private Instant createdAt;
    private Instant updatedAt;
}
