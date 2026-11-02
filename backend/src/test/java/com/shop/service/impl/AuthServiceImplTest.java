package com.shop.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.shop.dto.auth.LoginRequest;
import com.shop.dto.auth.RegisterRequest;
import com.shop.entity.PasswordResetToken;
import com.shop.entity.Role;
import com.shop.entity.User;
import com.shop.exception.ApiException;
import com.shop.exception.AuthException;
import com.shop.exception.BadRequestException;
import com.shop.exception.ConflictException;
import com.shop.mapper.UserMapperImpl;
import com.shop.repository.PasswordResetTokenRepository;
import com.shop.repository.RoleRepository;
import com.shop.repository.UserRepository;
import com.shop.security.ClientInfo;
import com.shop.security.RateLimiter;
import com.shop.security.TokenService;
import com.shop.service.AuthService.AuthResult;
import com.shop.service.PasswordResetNotifier;
import com.shop.support.MutableClock;
import com.shop.support.TestSecurity;
import com.shop.util.SecureTokens;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "JUnit");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordResetTokenRepository resetTokenRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private PasswordResetNotifier notifier;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-02T10:00:00Z"));
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, roleRepository, resetTokenRepository, encoder, tokenService,
                new RateLimiter(clock), notifier, new UserMapperImpl(), TestSecurity.properties(),
                TestSecurity.app(), clock);
        lenient().when(tokenService.createRefreshToken(any(), any()))
                .thenReturn(new TokenService.IssuedToken("refresh-raw", clock.instant().plus(Duration.ofDays(7))));
        lenient().when(tokenService.createAccessToken(any()))
                .thenReturn(new TokenService.IssuedToken("access-jwt", clock.instant().plus(Duration.ofMinutes(15))));
    }

    @Test
    void registerNormalizesEmailHashesPasswordAndAssignsCustomerRole() {
        when(roleRepository.findByName(Role.CUSTOMER)).thenReturn(Optional.of(role(Role.CUSTOMER)));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);

        AuthResult result = service.register(
                new RegisterRequest("  Asha Patil ", " Asha@Example.COM ", "", "Secret123"), CLIENT);

        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("asha@example.com");
        assertThat(user.getFullName()).isEqualTo("Asha Patil");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getPasswordHash()).isNotEqualTo("Secret123").startsWith("$2a$");
        assertThat(encoder.matches("Secret123", user.getPasswordHash())).isTrue();
        assertThat(user.hasRole(Role.CUSTOMER)).isTrue();
        assertThat(result.response().accessToken()).isEqualTo("access-jwt");
        assertThat(result.response().expiresIn()).isEqualTo(900);
        assertThat(result.refreshToken()).isEqualTo("refresh-raw");
    }

    @Test
    void registerRejectsDuplicateEmailAndPhone() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(
                new RegisterRequest("A B", "taken@example.com", null, "Secret123"), CLIENT))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("EMAIL_ALREADY_REGISTERED"));

        when(userRepository.existsByPhone("9876543210")).thenReturn(true);
        assertThatThrownBy(() -> service.register(
                new RegisterRequest("A B", "new@example.com", "9876543210", "Secret123"), CLIENT))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PHONE_ALREADY_REGISTERED"));
    }

    @Test
    void registerRejectsPasswordsLongerThanBcryptCanHash() {
        String multiByte = "पासवर्ड1".repeat(5);
        assertThatThrownBy(() -> service.register(new RegisterRequest("A B", "x@example.com", null, multiByte), CLIENT))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void loginSucceedsAndResetsFailureCounter() {
        User user = existing("asha@example.com", "Secret123");
        user.setFailedLoginAttempts(3);

        AuthResult result = service.login(new LoginRequest("ASHA@example.com", "Secret123"), CLIENT);

        assertThat(result.response().user().email()).isEqualTo("asha@example.com");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(clock.instant());
    }

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameError() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        existing("asha@example.com", "Secret123");

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "Secret123"), CLIENT))
                .isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_CREDENTIALS"));
        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Wrong1234"), CLIENT))
                .isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void fifthFailureLocksTheAccountAndLockExpires() {
        User user = existing("asha@example.com", "Secret123");
        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Wrong1234"), CLIENT))
                    .isInstanceOf(AuthException.class);
        }
        assertThat(user.getFailedLoginAttempts()).isEqualTo(4);

        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Wrong1234"), CLIENT))
                .isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_LOCKED"));
        assertThat(user.getLockedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));

        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Secret123"), CLIENT))
                .isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_LOCKED"));

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThat(service.login(new LoginRequest("asha@example.com", "Secret123"), CLIENT).refreshToken())
                .isEqualTo("refresh-raw");
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void disabledAccountIsOnlyRevealedAfterCorrectPassword() {
        User user = existing("asha@example.com", "Secret123");
        user.setEnabled(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Wrong1234"), CLIENT))
                .isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_CREDENTIALS"));
        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Secret123"), CLIENT))
                .isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_DISABLED"));
    }

    @Test
    void forgotPasswordForUnknownEmailSendsNothing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("nobody@example.com", CLIENT);

        verify(notifier, never()).sendPasswordResetLink(any(), anyString(), any());
        verify(resetTokenRepository, never()).save(any());
    }

    @Test
    void forgotPasswordStoresHashAndSendsLinkWithRawToken() {
        User user = existing("asha@example.com", "Secret123");
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);

        service.requestPasswordReset("asha@example.com", CLIENT);

        verify(resetTokenRepository).invalidateAllForUser(user.getId(), clock.instant());
        verify(resetTokenRepository).save(saved.capture());
        verify(notifier).sendPasswordResetLink(eq(user), link.capture(), eq(clock.instant().plus(Duration.ofMinutes(30))));
        assertThat(link.getValue()).startsWith("http://localhost:4200/reset-password?token=");
        String raw = link.getValue().substring(link.getValue().indexOf('=') + 1);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(SecureTokens.sha256Hex(raw));
    }

    @Test
    void resetPasswordChangesHashClearsLockAndRevokesSessions() {
        User user = existing("asha@example.com", "Secret123");
        user.setLockedUntil(clock.instant().plusSeconds(600));
        PasswordResetToken token = resetToken(user, "reset-raw", clock.instant().plusSeconds(60), null);

        service.resetPassword("reset-raw", "NewSecret456");

        assertThat(encoder.matches("NewSecret456", user.getPasswordHash())).isTrue();
        assertThat(user.getLockedUntil()).isNull();
        verify(resetTokenRepository).invalidateAllForUser(user.getId(), clock.instant());
        verify(tokenService).revokeAll(user.getId());
        assertThat(token.getUser()).isSameAs(user);
    }

    @Test
    void resetPasswordRejectsUsedExpiredAndUnknownTokens() {
        User user = existing("asha@example.com", "Secret123");
        resetToken(user, "used", clock.instant().plusSeconds(60), clock.instant().minusSeconds(1));
        resetToken(user, "expired", clock.instant().minusSeconds(1), null);
        when(resetTokenRepository.findByTokenHashForUpdate(SecureTokens.sha256Hex("unknown"))).thenReturn(Optional.empty());

        for (String raw : new String[] {"used", "expired", "unknown"}) {
            assertThatThrownBy(() -> service.resetPassword(raw, "NewSecret456"))
                    .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_RESET_TOKEN"));
        }
        verify(tokenService, never()).revokeAll(any());
    }

    private User existing(String email, String password) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 5L);
        user.setEmail(email);
        user.setFullName("Asha Patil");
        user.setPasswordHash(encoder.encode(password));
        user.getRoles().add(role(Role.CUSTOMER));
        lenient().when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    private PasswordResetToken resetToken(User user, String raw, Instant expiresAt, Instant usedAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.sha256Hex(raw));
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        when(resetTokenRepository.findByTokenHashForUpdate(SecureTokens.sha256Hex(raw))).thenReturn(Optional.of(token));
        return token;
    }

    private static Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
