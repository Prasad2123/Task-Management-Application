package com.fieldservice.service;

import com.fieldservice.dto.LoginRequest;
import com.fieldservice.dto.LoginResponse;
import com.fieldservice.entity.UserEntity;
import com.fieldservice.repository.UserRepository;
import com.fieldservice.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /**
     * Authenticate user by email and BCrypt password.
     * Returns JWT token and user info on success.
     * Throws BadCredentialsException on failure (same message for both invalid
     * email and wrong password to prevent user enumeration).
     */
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = tokenProvider.generateToken(
                user.getId(), user.getEmail(), user.getRole().name());

        log.info("User {} ({}) logged in successfully", user.getEmail(), user.getRole());

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInMs(tokenProvider.getExpirationMs())
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .phone(user.getPhone())
                .build();
    }
}
