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
public class ApprovalResponse {
    private Long id;
    private Long workId;
    private WorkResponse.UserSummary approver;
    private String approverRole;
    private String status;
    private Instant decidedAt;
    private String rejectionReason;
    private Instant createdAt;
    private Instant updatedAt;
}
