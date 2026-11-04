package com.shop.notification;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.shop.config.SecurityProperties;
import com.shop.entity.User;
import com.shop.service.PasswordResetNotifier;

/**
 * Emails the password reset link (Phase 17). In development the link can also be logged
 * (app.security.log-password-reset-links=true) so the flow works without a mail server.
 */
@Component
public class EmailPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailPasswordResetNotifier.class);

    private final EmailSender email;
    private final EmailTemplates templates;
    private final NotificationExecutor executor;
    private final boolean logLinks;

    public EmailPasswordResetNotifier(EmailSender email, EmailTemplates templates, NotificationExecutor executor,
            SecurityProperties properties) {
        this.email = email;
        this.templates = templates;
        this.executor = executor;
        this.logLinks = properties.logPasswordResetLinks();
    }

    @Override
    public void sendPasswordResetLink(User user, String resetLink, Instant expiresAt) {
        if (logLinks) {
            log.info("[DEV] Password reset link for {} (valid until {}): {}", user.getEmail(), expiresAt, resetLink);
        }
        EmailTemplates.Email m = templates.passwordReset(user.getFullName(), resetLink, expiresAt);
        String to = user.getEmail();
        executor.run(() -> email.send(to, m.subject(), m.html(), m.text()));
    }
}
