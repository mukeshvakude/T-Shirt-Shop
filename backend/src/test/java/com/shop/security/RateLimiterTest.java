package com.shop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.shop.exception.TooManyRequestsException;
import com.shop.support.MutableClock;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-02T10:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allowsUpToTheLimitThenRejectsWithRetryAfter() {
        for (int i = 0; i < 3; i++) {
            limiter.check("login:1.2.3.4", 3, Duration.ofMinutes(1));
        }
        clock.advance(Duration.ofSeconds(20));

        assertThatThrownBy(() -> limiter.check("login:1.2.3.4", 3, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(40));
    }

    @Test
    void startsAFreshWindowAfterExpiry() {
        for (int i = 0; i < 3; i++) {
            limiter.check("k", 3, Duration.ofMinutes(1));
        }
        clock.advance(Duration.ofSeconds(60));

        limiter.check("k", 3, Duration.ofMinutes(1));
    }

    @Test
    void keysAreIndependent() {
        limiter.check("login:a", 1, Duration.ofMinutes(1));
        limiter.check("login:b", 1, Duration.ofMinutes(1));

        assertThatThrownBy(() -> limiter.check("login:a", 1, Duration.ofMinutes(1)))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
