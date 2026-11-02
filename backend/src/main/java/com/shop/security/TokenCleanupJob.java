package com.shop.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.shop.repository.PasswordResetTokenRepository;
import com.shop.repository.RefreshTokenRepository;

/**
 * Deletes refresh and reset tokens that expired more than a day ago. Safe to run
 * on several instances at once because the deletes are idempotent.
 */
@Component
public class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);
    private static final Duration KEEP_AFTER_EXPIRY = Duration.ofDays(1);

    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final Clock clock;

    public TokenCleanupJob(RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
            Clock clock) {
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.jobs.token-cleanup-cron:0 30 3 * * *}", zone = "UTC")
    @Transactional
    public void purgeExpiredTokens() {
        Instant cutoff = clock.instant().minus(KEEP_AFTER_EXPIRY);
        int refresh = refreshTokens.deleteExpiredBefore(cutoff);
        int reset = resetTokens.deleteExpiredBefore(cutoff);
        if (refresh + reset > 0) {
            log.info("Purged {} refresh and {} password reset tokens", refresh, reset);
        }
    }
}
