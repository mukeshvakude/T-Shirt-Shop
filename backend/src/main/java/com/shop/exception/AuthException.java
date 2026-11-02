package com.shop.exception;

import org.springframework.http.HttpStatus;

/**
 * Authentication and account errors (invalid credentials, locked or disabled account,
 * invalid refresh or reset token).
 */
public class AuthException extends ApiException {

    public AuthException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
    }

    public static AuthException accountLocked() {
        return new AuthException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                "Too many failed login attempts. The account is temporarily locked, please try again later.");
    }

    public static AuthException accountDisabled() {
        return new AuthException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                "This account has been disabled. Please contact support.");
    }

    public static AuthException invalidRefreshToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "Your session has expired. Please log in again.");
    }

    public static AuthException invalidResetToken() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                "This password reset link is invalid or has expired. Please request a new one.");
    }
}
