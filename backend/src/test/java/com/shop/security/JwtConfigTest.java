package com.shop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

import com.shop.config.SecurityProperties;
import com.shop.support.TestSecurity;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();
    private final SecurityProperties props = TestSecurity.properties();

    @Test
    void rejectsMissingNonBase64AndShortSecrets() {
        assertThatThrownBy(() -> JwtConfig.toKey(null)).hasMessageContaining("not set");
        assertThatThrownBy(() -> JwtConfig.toKey("change-me!")).hasMessageContaining("Base64");
        String short31 = Base64.getEncoder().encodeToString(new byte[31]);
        assertThatThrownBy(() -> JwtConfig.toKey(short31)).hasMessageContaining("at least 32 bytes");
    }

    @Test
    void acceptsA256BitSecret() {
        assertThat(JwtConfig.toKey(Base64.getEncoder().encodeToString(new byte[32])).getAlgorithm())
                .isEqualTo("HmacSHA256");
    }

    @Test
    void decodesTokensItSignedAndMapsRolesToAuthorities() {
        SecretKey key = config.jwtSigningKey(props);
        JwtEncoder encoder = config.jwtEncoder(key);
        JwtDecoder decoder = config.jwtDecoder(key, props);

        Jwt jwt = decoder.decode(encode(encoder, props.jwt().issuer(), Instant.now().plusSeconds(60)));

        assertThat(jwt.getSubject()).isEqualTo("42");
        List<String> authorities = config.jwtAuthenticationConverter().convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
        // Spring Security 7 also adds a FACTOR_BEARER authority recording how the user authenticated.
        assertThat(authorities).contains("ROLE_CUSTOMER", "ROLE_ADMIN")
                .noneMatch(a -> a.startsWith("SCOPE_") || a.startsWith("ROLE_ROLE_"));
    }

    @Test
    void rejectsWrongIssuerExpiredAndForeignKey() {
        SecretKey key = config.jwtSigningKey(props);
        JwtEncoder encoder = config.jwtEncoder(key);
        JwtDecoder decoder = config.jwtDecoder(key, props);

        assertThatThrownBy(() -> decoder.decode(encode(encoder, "someone-else", Instant.now().plusSeconds(60))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(encode(encoder, props.jwt().issuer(), Instant.now().minusSeconds(120))))
                .isInstanceOf(JwtException.class);

        SecretKey otherKey = JwtConfig.toKey(Base64.getEncoder().encodeToString(new byte[48]));
        String forged = encode(config.jwtEncoder(otherKey), props.jwt().issuer(), Instant.now().plusSeconds(60));
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    private static String encode(JwtEncoder encoder, String issuer, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("42")
                .issuedAt(expiresAt.minusSeconds(900))
                .expiresAt(expiresAt)
                .claim(JwtConfig.ROLES_CLAIM, List.of("ROLE_CUSTOMER", "ROLE_ADMIN"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
