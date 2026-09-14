package com.fieldservice.service;

import com.fieldservice.dto.LoginRequest;
import com.fieldservice.dto.LoginResponse;
import com.fieldservice.entity.UserEntity;
import com.fieldservice.entity.UserRole;
import com.fieldservice.repository.UserRepository;
import com.fieldservice.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    private static final String PLAIN_PASSWORD = "demo1234";
    private static final String HASHED_PASSWORD = new BCryptPasswordEncoder().encode(PLAIN_PASSWORD);

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenProvider);
    }

    @Test
    void login_withValidCredentials_returnsToken() {
        // Arrange
        UserEntity user = UserEntity.builder()
                .id(1L)
                .name("Rahul Patil")
                .email("service@demo.com")
                .passwordHash(HASHED_PASSWORD)
                .role(UserRole.SERVICE_BOY)
                .active(true)
                .build();

        when(userRepository.findByEmail("service@demo.com")).thenReturn(Optional.of(user));
        when(tokenProvider.generateToken(1L, "service@demo.com", "SERVICE_BOY"))
                .thenReturn("test.jwt.token");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        LoginRequest request = new LoginRequest();
        request.setEmail("service@demo.com");
        request.setPassword(PLAIN_PASSWORD);

        // Act
        LoginResponse response = authService.login(request);

        // Assert
        assertThat(response.getAccessToken()).isEqualTo("test.jwt.token");
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getRole()).isEqualTo("SERVICE_BOY");
        assertThat(response.getName()).isEqualTo("Rahul Patil");
    }

    @Test
    void login_withWrongPassword_throwsBadCredentials() {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .email("service@demo.com")
                .passwordHash(HASHED_PASSWORD)
                .role(UserRole.SERVICE_BOY)
                .active(true)
                .build();

        when(userRepository.findByEmail("service@demo.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setEmail("service@demo.com");
        request.setPassword("wrongpassword");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_withUnknownEmail_throwsBadCredentials() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setEmail("unknown@test.com");
        request.setPassword("anypass");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_withInactiveUser_throwsBadCredentials() {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .email("inactive@demo.com")
                .passwordHash(HASHED_PASSWORD)
                .role(UserRole.SERVICE_BOY)
                .active(false)
                .build();

        when(userRepository.findByEmail("inactive@demo.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setEmail("inactive@demo.com");
        request.setPassword(PLAIN_PASSWORD);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void bcrypt_passwordHashing_isCorrect() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String hashed = encoder.encode(PLAIN_PASSWORD);
        assertThat(encoder.matches(PLAIN_PASSWORD, hashed)).isTrue();
        assertThat(encoder.matches("wrongpass", hashed)).isFalse();
        // Hash should not be the same as plain text
        assertThat(hashed).isNotEqualTo(PLAIN_PASSWORD);
    }

    @Test
    void loginResponse_doesNotContainPasswordHash() {
        UserEntity user = UserEntity.builder()
                .id(2L)
                .name("Amit Sharma")
                .email("poc@demo.com")
                .passwordHash(HASHED_PASSWORD)
                .phone("+91 87654 32109")
                .role(UserRole.POC)
                .active(true)
                .build();

        when(userRepository.findByEmail("poc@demo.com")).thenReturn(Optional.of(user));
        when(tokenProvider.generateToken(any(), any(), any())).thenReturn("poc.token");
        when(tokenProvider.getExpirationMs()).thenReturn(86400000L);

        LoginRequest request = new LoginRequest();
        request.setEmail("poc@demo.com");
        request.setPassword(PLAIN_PASSWORD);

        LoginResponse response = authService.login(request);

        // Verify the response object has no password field visible
        assertThat(response.toString()).doesNotContain(HASHED_PASSWORD);
        assertThat(response.toString()).doesNotContain("passwordHash");
        assertThat(response.getRole()).isEqualTo("POC");
    }
}
