package com.fieldservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {
    private String decision; // "APPROVED" or "REJECTED"
    private String reason;   // Mandatory if decision is "REJECTED"

    public ApprovalRequest(String reason) {
        this.reason = reason;
    }
}
