package com.shop.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.auth.AuthResponse;
import com.shop.dto.auth.ForgotPasswordRequest;
import com.shop.dto.auth.LoginRequest;
import com.shop.dto.auth.RegisterRequest;
import com.shop.dto.auth.ResetPasswordRequest;
import com.shop.dto.auth.UserResponse;
import com.shop.security.ClientInfo;
import com.shop.security.RefreshCookies;
import com.shop.service.AuthService;
import com.shop.service.AuthService.AuthResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Authentication endpoints. The access token is returned in the body and sent by
 * the client as "Authorization: Bearer ...". The refresh token lives only in an
 * HttpOnly, SameSite=Strict cookie scoped to /api/auth.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh, logout and password reset")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookies cookies;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Create a customer account and log in")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletRequest http) {
        return withSession(HttpStatus.CREATED, authService.register(request, ClientInfo.from(http)), cookies);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withSession(HttpStatus.OK, authService.login(request, ClientInfo.from(http)), cookies);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Get a new access token using the refresh-token cookie (rotates it). 204 when there is no cookie")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest http) {
        String refreshToken = cookies.read(http);
        if (refreshToken == null || refreshToken.isBlank()) {
            // No session cookie at all: an anonymous visitor, not an error.
            return ResponseEntity.noContent().build();
        }
        return withSession(HttpStatus.OK, authService.refresh(refreshToken, ClientInfo.from(http)), cookies);
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Revoke the current refresh token and clear the cookie")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        authService.logout(cookies.read(http));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements
    @Operation(summary = "Send a password reset link. Always returns 202, whether or not the email exists")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest http) {
        authService.requestPasswordReset(request.email(), ClientInfo.from(http));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    @Operation(summary = "Set a new password using a reset token. Logs out all sessions")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "The logged-in user")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.getUser(Long.valueOf(jwt.getSubject()));
    }

    /** Response carrying a new session: access token in the body, refresh token in the cookie. */
    public static ResponseEntity<AuthResponse> withSession(HttpStatus status, AuthResult result, RefreshCookies cookies) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.issue(result.refreshToken(), result.refreshTokenExpiresAt()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.response());
    }
}
