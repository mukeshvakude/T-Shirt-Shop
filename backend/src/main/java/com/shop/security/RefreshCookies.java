package com.shop.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.shop.config.SecurityProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/** Builds, clears and reads the HttpOnly refresh-token cookie. */
@Component
public class RefreshCookies {

    private final SecurityProperties.RefreshCookie config;
    private final Clock clock;

    public RefreshCookies(SecurityProperties properties, Clock clock) {
        this.config = properties.refreshCookie();
        this.clock = clock;
    }

    public String issue(String token, Instant expiresAt) {
        Duration maxAge = Duration.between(clock.instant(), expiresAt);
        return build(token, maxAge.isNegative() ? Duration.ZERO : maxAge);
    }

    public String clear() {
        return build("", Duration.ZERO);
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> config.name().equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(config.name(), value)
                .httpOnly(true)
                .secure(config.secure())
                .sameSite(config.sameSite())
                .path(config.path())
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
