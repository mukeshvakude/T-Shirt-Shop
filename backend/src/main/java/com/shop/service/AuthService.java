package com.shop.service;

import java.time.Instant;

import com.shop.dto.auth.AuthResponse;
import com.shop.dto.auth.LoginRequest;
import com.shop.dto.auth.RegisterRequest;
import com.shop.dto.auth.UserResponse;
import com.shop.security.ClientInfo;

public interface AuthService {

    /** Body for the client plus the raw refresh token, which the controller puts in a cookie. */
    record AuthResult(AuthResponse response, String refreshToken, Instant refreshTokenExpiresAt) {
    }

    AuthResult register(RegisterRequest request, ClientInfo client);

    AuthResult login(LoginRequest request, ClientInfo client);

    AuthResult refresh(String refreshToken, ClientInfo client);

    void logout(String refreshToken);

    /** Always completes normally so the response does not reveal whether the email exists. */
    void requestPasswordReset(String email, ClientInfo client);

    void resetPassword(String token, String newPassword);

    UserResponse getUser(Long userId);

    /** A fresh access + refresh token pair for an already authenticated user (e.g. after a password change). */
    AuthResult issueSession(Long userId, ClientInfo client);
}
