package com.shop.service;

import java.time.Instant;

import com.shop.entity.User;

/**
 * Delivers password reset links. Phase 17 adds the email implementation.
 */
public interface PasswordResetNotifier {

    void sendPasswordResetLink(User user, String resetLink, Instant expiresAt);
}
