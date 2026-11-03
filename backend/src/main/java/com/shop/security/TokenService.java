package com.shop.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.config.SecurityProperties;
import com.shop.entity.RefreshToken;
import com.shop.entity.Role;
import com.shop.entity.User;
import com.shop.exception.AuthException;
import com.shop.repository.RefreshTokenRepository;
import com.shop.util.SecureTokens;

import lombok.RequiredArgsConstructor;

/**
 * Issues short-lived JWT access tokens and long-lived rotating refresh tokens.
 * Re-use of an already rotated refresh token revokes every session of that user.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    /**
     * A rotated token replayed this soon after rotation, while its successor was never used, is
     * treated as a lost response (page reloaded or closed during the refresh call), not theft.
     */
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public record Rotation(User user, IssuedToken refreshToken) {
    }

    public IssuedToken createAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim(JwtConfig.ROLES_CLAIM, user.getRoles().stream().map(Role::getName).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    @Transactional
    public IssuedToken createRefreshToken(User user, ClientInfo client) {
        return save(user, client).token();
    }

    /**
     * Validates a refresh token and replaces it with a new one.
     * Callers must not roll back on AuthException, so a detected re-use stays revoked.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public Rotation rotate(String rawToken, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(SecureTokens.sha256Hex(rawToken))
                .orElseThrow(AuthException::invalidRefreshToken);
        User user = current.getUser();

        if (current.getRevokedAt() != null) {
            if (current.getReplacedBy() != null && current.getRevokedAt().isAfter(now.minus(REUSE_GRACE))) {
                RefreshToken successor = refreshTokenRepository.findById(current.getReplacedBy()).orElse(null);
                if (successor != null && successor.isActiveAt(now) && user.isEnabled()) {
                    // The browser never stored the successor cookie: retire it and continue the session.
                    Saved next = save(user, client);
                    successor.setRevokedAt(now);
                    log.info("Refresh token replayed within {} s of rotation for user {}; session continued",
                            REUSE_GRACE.toSeconds(), user.getId());
                    return new Rotation(user, next.token());
                }
            }
            if (current.getReplacedBy() != null) {
                // A token that was already rotated is being replayed: likely stolen.
                int revoked = refreshTokenRepository.revokeAllForUser(user.getId(), now);
                log.warn("Refresh token re-use detected for user {}; revoked {} active session(s)",
                        user.getId(), revoked);
            }
            // Otherwise it was revoked by logout or password reset: simply invalid.
            throw AuthException.invalidRefreshToken();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw AuthException.invalidRefreshToken();
        }
        if (!user.isEnabled()) {
            refreshTokenRepository.revokeAllForUser(user.getId(), now);
            throw AuthException.accountDisabled();
        }

        Saved next = save(user, client);
        current.setRevokedAt(now);
        current.setReplacedBy(next.entity().getId());
        return new Rotation(user, next.token());
    }

    /** Revokes one refresh token if it exists and is still active. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(clock.instant()));
    }

    @Transactional
    public int revokeAll(Long userId) {
        return refreshTokenRepository.revokeAllForUser(userId, clock.instant());
    }

    private record Saved(RefreshToken entity, IssuedToken token) {
    }

    private Saved save(User user, ClientInfo client) {
        String raw = SecureTokens.newToken();
        Instant expiresAt = clock.instant().plus(properties.jwt().refreshTokenTtl());
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(SecureTokens.sha256Hex(raw));
        entity.setExpiresAt(expiresAt);
        entity.setUserAgent(client.userAgent());
        entity.setIpAddress(client.ipAddress());
        refreshTokenRepository.save(entity);
        return new Saved(entity, new IssuedToken(raw, expiresAt));
    }
}
