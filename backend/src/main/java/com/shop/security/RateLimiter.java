package com.shop.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.shop.exception.TooManyRequestsException;

/**
 * Fixed-window, in-memory rate limiter for sensitive endpoints (login, register,
 * forgot password). Counts are per application instance; with several instances
 * behind a load balancer, add a shared limiter at the proxy as well.
 */
@Component
public class RateLimiter {

    private static final int SWEEP_THRESHOLD = 10_000;

    private record Window(long expiresAtEpochSecond, int count) {
    }

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records one request for {@code key} and throws when more than {@code limit}
     * requests fall in the current window.
     */
    public void check(String key, int limit, Duration window) {
        long now = clock.instant().getEpochSecond();
        Window current = windows.compute(key, (k, old) -> old == null || old.expiresAtEpochSecond() <= now
                ? new Window(now + window.toSeconds(), 1)
                : new Window(old.expiresAtEpochSecond(), old.count() + 1));
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.entrySet().removeIf(e -> e.getValue().expiresAtEpochSecond() <= now);
        }
        if (current.count() > limit) {
            throw new TooManyRequestsException(current.expiresAtEpochSecond() - now);
        }
    }

    /** For tests and admin tooling. */
    public void reset() {
        windows.clear();
    }
}
