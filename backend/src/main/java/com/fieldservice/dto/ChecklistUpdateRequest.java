package com.fieldservice.dto;

import lombok.Data;

@Data
public class ChecklistUpdateRequest {
    private Boolean completed;  // null = no change, true/false = set state
}
