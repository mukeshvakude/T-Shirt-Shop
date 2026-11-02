package com.shop.service.impl;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.shop.config.SecurityProperties;
import com.shop.entity.User;
import com.shop.service.PasswordResetNotifier;

/**
 * Temporary notifier until email delivery (Phase 17). The link itself is only
 * logged when app.security.log-password-reset-links is true (dev profile).
 */
@Component
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    private final boolean logLinks;

    public LoggingPasswordResetNotifier(SecurityProperties properties) {
        this.logLinks = properties.logPasswordResetLinks();
    }

    @Override
    public void sendPasswordResetLink(User user, String resetLink, Instant expiresAt) {
        if (logLinks) {
            log.info("[DEV] Password reset link for {} (valid until {}): {}", user.getEmail(), expiresAt, resetLink);
        } else {
            log.warn("Password reset requested for user {} but email delivery is not configured yet", user.getId());
        }
    }
}
