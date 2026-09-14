package com.fieldservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresInMs;
    private Long userId;
    private String name;
    private String email;
    private String role;
    private String phone;
    // NOTE: passwordHash is NEVER included
}
