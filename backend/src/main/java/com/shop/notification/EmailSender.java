package com.shop.notification;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.shop.config.AppProperties;

import jakarta.mail.internet.MimeMessage;

/**
 * Sends email through SMTP when MAIL_HOST and MAIL_FROM are configured; otherwise logs that an
 * email would have been sent (never the content, which can hold personal data or links).
 * Failures are logged and never break the action that triggered the email.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String from;
    private final String shopName;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender, @Value("${spring.mail.host:}") String host,
            @Value("${app.mail.from:}") String from, AppProperties properties) {
        this.mailSender = mailSender;
        this.host = host;
        this.from = from;
        this.shopName = properties.business() == null ? "T-Shirt Shop" : properties.business().displayName();
    }

    /** True when a real SMTP server and sender address are set (not the .env.example placeholders). */
    public boolean configured() {
        return host != null && !host.isBlank() && !host.equals("smtp.example.com") && from != null && from.contains("@")
                && !from.endsWith("@example.com") && mailSender.getIfAvailable() != null;
    }

    /** @return true when the email was handed to the mail server */
    public boolean send(String to, String subject, String html, String text) {
        if (to == null || to.isBlank()) {
            return false;
        }
        if (!configured()) {
            log.info("Email not sent (mail is not configured): \"{}\" to {}", subject, mask(to));
            return false;
        }
        try {
            JavaMailSender sender = mailSender.getObject();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, shopName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
            log.info("Email sent: \"{}\" to {}", subject, mask(to));
            return true;
        } catch (Exception ex) {
            log.error("Email \"{}\" to {} could not be sent: {}", subject, mask(to), ex.getMessage());
            return false;
        }
    }

    /** a***@example.com, so logs do not collect full addresses. */
    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }
}
