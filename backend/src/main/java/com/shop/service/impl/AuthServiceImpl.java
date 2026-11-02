package com.shop.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.config.AppProperties;
import com.shop.config.SecurityProperties;
import com.shop.dto.auth.AuthResponse;
import com.shop.dto.auth.LoginRequest;
import com.shop.dto.auth.PasswordRules;
import com.shop.dto.auth.RegisterRequest;
import com.shop.dto.auth.UserResponse;
import com.shop.entity.PasswordResetToken;
import com.shop.entity.Role;
import com.shop.entity.User;
import com.shop.exception.ApiException;
import com.shop.exception.AuthException;
import com.shop.exception.BadRequestException;
import com.shop.exception.ConflictException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.mapper.UserMapper;
import com.shop.repository.PasswordResetTokenRepository;
import com.shop.repository.RoleRepository;
import com.shop.repository.UserRepository;
import com.shop.security.ClientInfo;
import com.shop.security.RateLimiter;
import com.shop.security.TokenService;
import com.shop.security.TokenService.IssuedToken;
import com.shop.service.AuthService;
import com.shop.service.PasswordResetNotifier;
import com.shop.util.SecureTokens;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RateLimiter rateLimiter;
    private final PasswordResetNotifier resetNotifier;
    private final UserMapper userMapper;
    private final SecurityProperties security;
    private final AppProperties app;
    private final Clock clock;
    /** Compared against when the email is unknown, so response time does not reveal registered emails. */
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
            PasswordResetTokenRepository resetTokenRepository, PasswordEncoder passwordEncoder,
            TokenService tokenService, RateLimiter rateLimiter, PasswordResetNotifier resetNotifier,
            UserMapper userMapper, SecurityProperties security, AppProperties app, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.resetNotifier = resetNotifier;
        this.userMapper = userMapper;
        this.security = security;
        this.app = app;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-" + SecureTokens.newToken());
    }

    @Override
    @Transactional
    public AuthResult register(RegisterRequest request, ClientInfo client) {
        rateLimiter.check("register:" + client.ipAddress(), security.rateLimit().registerPerHour(), Duration.ofHours(1));
        String email = normalizeEmail(request.email());
        String phone = blankToNull(request.phone());
        requireBcryptLength(request.password());

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new ConflictException("PHONE_ALREADY_REGISTERED", "An account with this phone number already exists.");
        }

        User user = new User();
        user.setEmail(email);
        user.setPhone(phone);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(role(Role.CUSTOMER));
        user.setLastLoginAt(clock.instant());
        userRepository.save(user);
        log.info("Registered customer {}", user.getId());
        return issueTokens(user, client);
    }

    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(LoginRequest request, ClientInfo client) {
        rateLimiter.check("login:" + client.ipAddress(), security.rateLimit().loginPerMinute(), Duration.ofMinutes(1));
        Instant now = clock.instant();
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw AuthException.invalidCredentials();
        }
        if (user.isLockedAt(now)) {
            throw AuthException.accountLocked();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user, now);
        }
        if (!user.isEnabled()) {
            throw AuthException.accountDisabled();
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        return issueTokens(user, client);
    }

    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String refreshToken, ClientInfo client) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw AuthException.invalidRefreshToken();
        }
        TokenService.Rotation rotation = tokenService.rotate(refreshToken, client);
        return buildResult(rotation.user(), rotation.refreshToken());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenService.revoke(refreshToken);
        }
    }

    @Override
    @Transactional
    public void requestPasswordReset(String email, ClientInfo client) {
        rateLimiter.check("forgot:" + client.ipAddress(), security.rateLimit().forgotPasswordPerHour(),
                Duration.ofHours(1));
        userRepository.findByEmail(normalizeEmail(email))
                .filter(User::isEnabled)
                .ifPresent(this::createAndSendResetToken);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHashForUpdate(SecureTokens.sha256Hex(token))
                .filter(t -> t.isUsableAt(now))
                .orElseThrow(AuthException::invalidResetToken);
        requireBcryptLength(newPassword);

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        resetTokenRepository.invalidateAllForUser(user.getId(), now);
        int sessions = tokenService.revokeAll(user.getId());
        log.info("Password reset for user {}; revoked {} session(s)", user.getId(), sessions);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return userRepository.findWithRolesById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private void registerFailedAttempt(User user, Instant now) {
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= security.login().maxFailedAttempts()) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(now.plus(security.login().lockDuration()));
            log.warn("Locked user {} after {} failed login attempts", user.getId(), attempts);
            throw AuthException.accountLocked();
        }
        user.setFailedLoginAttempts(attempts);
        throw AuthException.invalidCredentials();
    }

    private void createAndSendResetToken(User user) {
        Instant now = clock.instant();
        resetTokenRepository.invalidateAllForUser(user.getId(), now);
        String raw = SecureTokens.newToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.sha256Hex(raw));
        token.setExpiresAt(now.plus(security.passwordResetTtl()));
        resetTokenRepository.save(token);
        String link = app.frontendUrl().replaceAll("/+$", "") + "/reset-password?token=" + raw;
        resetNotifier.sendPasswordResetLink(user, link, token.getExpiresAt());
    }

    private AuthResult issueTokens(User user, ClientInfo client) {
        return buildResult(user, tokenService.createRefreshToken(user, client));
    }

    private AuthResult buildResult(User user, IssuedToken refreshToken) {
        IssuedToken access = tokenService.createAccessToken(user);
        long expiresIn = security.jwt().accessTokenTtl().toSeconds();
        AuthResponse body = AuthResponse.bearer(access.value(), expiresIn, access.expiresAt(),
                userMapper.toResponse(user));
        return new AuthResult(body, refreshToken.value(), refreshToken.expiresAt());
    }

    private Role role(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Missing role " + name + " (Flyway V2 seed)"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireBcryptLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > PasswordRules.MAX_BYTES) {
            throw new BadRequestException("PASSWORD_TOO_LONG", "Password is too long.");
        }
    }
}
