package com.shop.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Authentication settings bound from {@code app.security.*}.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid RefreshCookie refreshCookie,
        @NotNull Duration passwordResetTtl,
        @NotNull @Valid Login login,
        @NotNull @Valid RateLimit rateLimit,
        @NotNull Admin admin,
        boolean logPasswordResetLinks) {

    /**
     * @param secret Base64-encoded HMAC key, at least 32 bytes once decoded (env JWT_SECRET)
     */
    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    public record RefreshCookie(@NotBlank String name, @NotBlank String path, boolean secure,
            @NotBlank String sameSite) {
    }

    public record Login(@Min(1) int maxFailedAttempts, @NotNull Duration lockDuration) {
    }

    public record RateLimit(@Min(1) int loginPerMinute, @Min(1) int registerPerHour,
            @Min(1) int forgotPasswordPerHour, @Min(1) int newsletterPerHour, @Min(1) int contactPerHour) {
    }

    /** First admin account, created at start-up when no admin exists and both values are set. */
    public record Admin(String email, String password, String fullName) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
