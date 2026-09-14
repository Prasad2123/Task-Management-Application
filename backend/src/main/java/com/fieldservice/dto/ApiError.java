package com.fieldservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {
    private int status;
    private String error;
    private String message;
    private long timestamp;

    public static ApiError of(int status, String error, String message) {
        return ApiError.builder()
                .status(status)
                .error(error)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
