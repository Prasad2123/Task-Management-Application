package com.fieldservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
                "TestSecretKeyForUnitTestingPurposeOnlyMinimum256BitsLong");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationMs", 3600000L);
    }

    @Test
    void generateToken_returnsNonNullToken() {
        String token = tokenProvider.generateToken(1L, "test@test.com", "SERVICE_BOY");
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void validateToken_withValidToken_returnsTrue() {
        String token = tokenProvider.generateToken(1L, "test@test.com", "SERVICE_BOY");
        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_withInvalidToken_returnsFalse() {
        assertThat(tokenProvider.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    void getUserIdFromToken_returnsCorrectId() {
        String token = tokenProvider.generateToken(42L, "test@test.com", "POC");
        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void getRoleFromToken_returnsCorrectRole() {
        String token = tokenProvider.generateToken(1L, "test@test.com", "SITE_SUPERVISOR");
        assertThat(tokenProvider.getRoleFromToken(token)).isEqualTo("SITE_SUPERVISOR");
    }

    @Test
    void validateToken_withExpiredToken_returnsFalse() {
        // Token with -1ms expiration (already expired)
        JwtTokenProvider expiredProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(expiredProvider, "jwtSecret",
                "TestSecretKeyForUnitTestingPurposeOnlyMinimum256BitsLong");
        ReflectionTestUtils.setField(expiredProvider, "jwtExpirationMs", -1000L);

        String token = expiredProvider.generateToken(1L, "test@test.com", "SERVICE_BOY");
        assertThat(tokenProvider.validateToken(token)).isFalse();
    }

    @Test
    void generateToken_doesNotContainSensitiveData() {
        String token = tokenProvider.generateToken(1L, "test@test.com", "SERVICE_BOY");
        // JWT tokens are Base64 encoded — the password hash should NEVER appear
        assertThat(token).doesNotContain("password");
        assertThat(token).doesNotContain("hash");
    }
}
