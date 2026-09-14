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
public class WorkReportDto {
    private Long id;
    private Long workId;
    private String reportNumber;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String generatedAt;
    private Integer version;
    private String downloadUrl;
    private String duration;
    private String workTitle;
    private String clientName;
    private String completedAt;
}
