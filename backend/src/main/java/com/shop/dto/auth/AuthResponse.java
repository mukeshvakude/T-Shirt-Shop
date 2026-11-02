package com.shop.dto.auth;

import java.time.Instant;

/**
 * Returned by register, login and refresh. The refresh token is never in the body:
 * it is set as an HttpOnly cookie.
 *
 * @param expiresIn access token lifetime in seconds
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt,
        UserResponse user) {

    public static AuthResponse bearer(String accessToken, long expiresIn, Instant expiresAt, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, expiresAt, user);
    }
}
