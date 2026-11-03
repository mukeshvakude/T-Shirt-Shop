package com.shop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import com.shop.config.SecurityProperties;
import com.shop.entity.RefreshToken;
import com.shop.entity.Role;
import com.shop.entity.User;
import com.shop.exception.AuthException;
import com.shop.repository.RefreshTokenRepository;
import com.shop.support.MutableClock;
import com.shop.support.TestSecurity;
import com.shop.util.SecureTokens;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final ClientInfo CLIENT = new ClientInfo("127.0.0.1", "JUnit");

    @Mock
    private RefreshTokenRepository repository;

    private final MutableClock clock = new MutableClock(Instant.now());
    private final SecurityProperties props = TestSecurity.properties();
    private final JwtConfig jwtConfig = new JwtConfig();
    private final AtomicLong ids = new AtomicLong(100);
    private TokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        var key = jwtConfig.jwtSigningKey(props);
        service = new TokenService(jwtConfig.jwtEncoder(key), props, repository, clock);
        user = user(7L, true);
    }

    @Test
    void accessTokenCarriesSubjectRolesAndExpiry() {
        TokenService.IssuedToken token = service.createAccessToken(user);

        Jwt jwt = jwtConfig.jwtDecoder(jwtConfig.jwtSigningKey(props), props).decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_CUSTOMER");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("user7@example.com");
        assertThat(token.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    }

    @Test
    void refreshTokenIsStoredOnlyAsHash() {
        stubSave();
        TokenService.IssuedToken token = service.createRefreshToken(user, CLIENT);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(t ->
                t.getTokenHash().equals(SecureTokens.sha256Hex(token.value()))
                        && !t.getTokenHash().equals(token.value())
                        && t.getExpiresAt().equals(clock.instant().plus(Duration.ofDays(7)))));
    }

    @Test
    void rotationRevokesTheOldTokenAndLinksTheNewOne() {
        stubSave();
        RefreshToken current = stored("raw-1", null, null, clock.instant().plus(Duration.ofDays(1)));

        TokenService.Rotation rotation = service.rotate("raw-1", CLIENT);

        assertThat(current.getRevokedAt()).isEqualTo(clock.instant());
        assertThat(current.getReplacedBy()).isNotNull();
        assertThat(rotation.refreshToken().value()).isNotEqualTo("raw-1");
        assertThat(rotation.user()).isSameAs(user);
    }

    @Test
    void replayOfARotatedTokenRevokesAllSessions() {
        stored("raw-1", clock.instant().minusSeconds(5), 99L, clock.instant().plus(Duration.ofDays(1)));

        assertThatThrownBy(() -> service.rotate("raw-1", CLIENT))
                .isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_REFRESH_TOKEN"));
        verify(repository).revokeAllForUser(7L, clock.instant());
    }

    @Test
    void replayRightAfterRotationWithUnusedSuccessorContinuesTheSession() {
        stubSave();
        stored("raw-1", clock.instant().minusSeconds(3), 99L, clock.instant().plus(Duration.ofDays(1)));
        RefreshToken successor = successor(99L, null);

        TokenService.Rotation rotation = service.rotate("raw-1", CLIENT);

        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.refreshToken().value()).isNotEqualTo("raw-1");
        assertThat(successor.getRevokedAt()).isEqualTo(clock.instant());
        verify(repository, never()).revokeAllForUser(anyLong(), any());
    }

    @Test
    void replayAfterTheGraceWindowIsTreatedAsTheft() {
        stored("raw-1", clock.instant().minus(TokenService.REUSE_GRACE).minusSeconds(1), 99L,
                clock.instant().plus(Duration.ofDays(1)));

        assertThatThrownBy(() -> service.rotate("raw-1", CLIENT)).isInstanceOf(AuthException.class);
        verify(repository).revokeAllForUser(7L, clock.instant());
    }

    @Test
    void replayAfterTheSuccessorWasUsedIsTreatedAsTheft() {
        stored("raw-1", clock.instant().minusSeconds(2), 99L, clock.instant().plus(Duration.ofDays(1)));
        successor(99L, clock.instant().minusSeconds(1));

        assertThatThrownBy(() -> service.rotate("raw-1", CLIENT)).isInstanceOf(AuthException.class);
        verify(repository).revokeAllForUser(7L, clock.instant());
    }

    private RefreshToken successor(Long id, Instant revokedAt) {
        RefreshToken next = new RefreshToken();
        next.setUser(user);
        next.setRevokedAt(revokedAt);
        next.setExpiresAt(clock.instant().plus(Duration.ofDays(7)));
        when(repository.findById(id)).thenReturn(Optional.of(next));
        return next;
    }

    @Test
    void loggedOutTokenIsInvalidWithoutRevokingOtherSessions() {
        stored("raw-1", clock.instant().minusSeconds(5), null, clock.instant().plus(Duration.ofDays(1)));

        assertThatThrownBy(() -> service.rotate("raw-1", CLIENT)).isInstanceOf(AuthException.class);
        verify(repository, never()).revokeAllForUser(anyLong(), any());
    }

    @Test
    void expiredAndUnknownTokensAreRejected() {
        stored("old", null, null, clock.instant().minusSeconds(1));
        when(repository.findByTokenHashForUpdate(SecureTokens.sha256Hex("unknown"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("old", CLIENT)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.rotate("unknown", CLIENT)).isInstanceOf(AuthException.class);
    }

    @Test
    void disabledUserCannotRefresh() {
        user.setEnabled(false);
        stored("raw-1", null, null, clock.instant().plus(Duration.ofDays(1)));

        assertThatThrownBy(() -> service.rotate("raw-1", CLIENT))
                .isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_DISABLED"));
        verify(repository).revokeAllForUser(7L, clock.instant());
    }

    private RefreshToken stored(String raw, Instant revokedAt, Long replacedBy, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.sha256Hex(raw));
        token.setRevokedAt(revokedAt);
        token.setReplacedBy(replacedBy);
        token.setExpiresAt(expiresAt);
        when(repository.findByTokenHashForUpdate(SecureTokens.sha256Hex(raw))).thenReturn(Optional.of(token));
        return token;
    }

    private void stubSave() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", ids.incrementAndGet());
            return t;
        });
    }

    static User user(Long id, boolean enabled) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setEmail("user" + id + "@example.com");
        u.setFullName("User " + id);
        u.setEnabled(enabled);
        Role role = new Role();
        role.setName(Role.CUSTOMER);
        u.getRoles().add(role);
        return u;
    }
}
