package com.shop.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Removes data created by integration tests so every test starts from the same state:
 * Flyway reference data plus the bootstrap admin account. Extend the list as new
 * phases add tables that tests write to (children before parents).
 */
public final class DatabaseCleaner {

    public static final String BOOTSTRAP_ADMIN_EMAIL = "admin@test.local";
    /** Products seeded by Flyway V2 (blank customizable T-Shirts). */
    private static final String SEEDED_PRODUCTS =
            "('custom-round-neck-t-shirt','custom-v-neck-t-shirt','custom-polo-t-shirt','custom-oversized-t-shirt')";
    private static final String SYSTEM_CATEGORIES = "('TSHIRTS','SHIRTS','PANTS','CUSTOM_TSHIRTS')";

    private DatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbc) {
        // Storefront engagement
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM addresses");
        jdbc.update("DELETE FROM wishlists");
        jdbc.update("DELETE FROM reviews");
        jdbc.update("DELETE FROM contact_messages");
        jdbc.update("DELETE FROM newsletter_subscribers");
        jdbc.update("DELETE FROM coupons");

        // Catalog created by tests
        jdbc.update("DELETE i FROM product_images i JOIN products p ON p.id = i.product_id WHERE p.slug NOT IN "
                + SEEDED_PRODUCTS);
        jdbc.update("DELETE v FROM inventory v JOIN products p ON p.id = v.product_id WHERE p.slug NOT IN "
                + SEEDED_PRODUCTS);
        jdbc.update("DELETE FROM products WHERE slug NOT IN " + SEEDED_PRODUCTS);
        jdbc.update("DELETE FROM categories WHERE code NOT IN " + SYSTEM_CATEGORIES);
        jdbc.update("UPDATE categories SET active = TRUE");

        // Accounts
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM password_reset_tokens");
        jdbc.update("DELETE ur FROM user_roles ur JOIN users u ON u.id = ur.user_id WHERE u.email <> ?",
                BOOTSTRAP_ADMIN_EMAIL);
        jdbc.update("DELETE FROM users WHERE email <> ?", BOOTSTRAP_ADMIN_EMAIL);
        jdbc.update("UPDATE users SET failed_login_attempts = 0, locked_until = NULL");
    }
}
