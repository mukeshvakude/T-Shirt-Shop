package com.shop.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.shop.notification.EmailSender;

/**
 * Stops a {@code prod} start with settings that would make the live shop unsafe or unable to take
 * payments, and warns about settings the owner still has to fill in. Values are never logged.
 */
@Component
@Profile("prod")
public class ProductionReadinessCheck implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProductionReadinessCheck.class);

    private final AppProperties app;
    private final SecurityProperties security;
    private final EmailSender email;

    public ProductionReadinessCheck(AppProperties app, SecurityProperties security, EmailSender email) {
        this.app = app;
        this.security = security;
        this.email = email;
    }

    @Override
    public void afterPropertiesSet() {
        Report report = check(app, security, email.configured());
        report.warnings().forEach(w -> log.warn("Production setting: {}", w));
        if (!report.errors().isEmpty()) {
            throw new IllegalStateException("Production settings are not safe to start with: " + String.join("; ", report.errors()));
        }
    }

    public record Report(List<String> errors, List<String> warnings) {
    }

    /** Pure check, unit tested. */
    public static Report check(AppProperties app, SecurityProperties security, boolean mailConfigured) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!app.frontendUrl().startsWith("https://")) {
            errors.add("APP_FRONTEND_URL must be the https:// address of the shop");
        }
        for (String origin : app.cors().allowedOrigins()) {
            if (!origin.startsWith("https://") || origin.contains("localhost") || origin.contains("127.0.0.1") || origin.contains("*")) {
                errors.add("APP_CORS_ALLOWED_ORIGINS must list only the https:// shop address (found a local, http or wildcard origin)");
                break;
            }
        }
        if (!security.refreshCookie().secure()) {
            errors.add("REFRESH_COOKIE_SECURE must be true");
        }
        if (security.logPasswordResetLinks()) {
            errors.add("Password reset links must not be written to the log (app.security.log-password-reset-links=false)");
        }
        if (app.apiDocsEnabled()) {
            errors.add("API docs must be off (app.api-docs-enabled=false)");
        }
        AppProperties.Payments payments = app.payments();
        if (payments == null || !payments.razorpayConfigured()) {
            errors.add("RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are required");
        } else if (payments.razorpayKeyId().startsWith("rzp_test_")) {
            warnings.add("Razorpay is in TEST mode (rzp_test_ key): customers cannot pay real money");
        }
        if (payments == null || !payments.webhookConfigured()) {
            warnings.add("RAZORPAY_WEBHOOK_SECRET is not set: payments are confirmed by the browser and the 5-minute reconciliation only");
        }
        if (!mailConfigured) {
            warnings.add("Email (MAIL_HOST, MAIL_FROM) is not set: order and password reset emails are not sent");
        }
        AppProperties.Business business = app.business();
        if (business == null || isBlank(business.legalName()) || isBlank(business.address())) {
            warnings.add("BUSINESS_LEGAL_NAME and BUSINESS_ADDRESS are empty: invoices show only the shop name");
        }
        if ("local".equals(app.storage().provider()) && !app.storage().localDir().startsWith("/")
                && !app.storage().localDir().matches("^[A-Za-z]:[\\/].*")) {
            warnings.add("STORAGE_LOCAL_DIR is a relative path: use an absolute folder that is backed up");
        }
        return new Report(List.copyOf(errors), List.copyOf(warnings));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
