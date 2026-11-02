package com.shop.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Removes data created by integration tests so every test starts from the same state:
 * Flyway reference data plus the bootstrap admin account. Extend the list as new
 * phases add tables that tests write to (children before parents).
 */
public final class DatabaseCleaner {

    public static final String BOOTSTRAP_ADMIN_EMAIL = "admin@test.local";

    private DatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM password_reset_tokens");
        jdbc.update("DELETE ur FROM user_roles ur JOIN users u ON u.id = ur.user_id WHERE u.email <> ?",
                BOOTSTRAP_ADMIN_EMAIL);
        jdbc.update("DELETE FROM users WHERE email <> ?", BOOTSTRAP_ADMIN_EMAIL);
        jdbc.update("UPDATE users SET failed_login_attempts = 0, locked_until = NULL");
    }
}
