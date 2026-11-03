package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;

/**
 * Storefront home page and engagement endpoints against MySQL (Phase 6).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
    }

    // ---------------------------------------------------------------- home

    @Test
    void homeOnFreshShopShowsOnlyWhatExists() throws Exception {
        mvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                .andExpect(jsonPath("$.categories", hasSize(4)))
                .andExpect(jsonPath("$.collections", hasSize(0)))
                .andExpect(jsonPath("$.featured", hasSize(0)))
                .andExpect(jsonPath("$.offers", hasSize(0)))
                .andExpect(jsonPath("$.reviews", hasSize(0)))
                .andExpect(jsonPath("$.customTshirt.available").value(true))
                .andExpect(jsonPath("$.customTshirt.fromPrice").value(299.00))
                .andExpect(jsonPath("$.customTshirt.tshirtTypes", contains("Round Neck", "V-Neck", "Polo", "Oversized")));
    }

    @Test
    void homeSectionsFollowProductFlags() throws Exception {
        long tees = id("SELECT id FROM categories WHERE code = 'TSHIRTS'");
        long pants = id("SELECT id FROM categories WHERE code = 'PANTS'");
        product(tees, "Featured Tee", "featured-tee", "599.00", null, true, false, 50);
        product(tees, "New Tee", "new-tee", "499.00", "399.00", false, true, 10);
        product(pants, "Plain Chinos", "plain-chinos", "1499.00", null, false, false, 5);
        jdbc.update("UPDATE products SET sold_count = 1000 WHERE slug LIKE 'custom-%'");

        mvc.perform(get("/api/home"))
                .andExpect(jsonPath("$.featured[*].slug", contains("featured-tee")))
                .andExpect(jsonPath("$.newArrivals[*].slug", contains("new-tee")))
                .andExpect(jsonPath("$.onSale[*].slug", contains("new-tee")))
                .andExpect(jsonPath("$.bestSellers[0].slug").value("featured-tee"))
                .andExpect(jsonPath("$.bestSellers[*].categoryCode", everyItem(not("CUSTOM_TSHIRTS"))))
                .andExpect(jsonPath("$.collections[*].category.code", contains("TSHIRTS", "PANTS")))
                .andExpect(jsonPath("$.collections[0].productCount").value(2))
                .andExpect(jsonPath("$.collections[1].products[0].name").value("Plain Chinos"));

        mvc.perform(get("/api/products").param("onSale", "true"))
                .andExpect(jsonPath("$.content[*].slug", contains("new-tee")));
    }

    // ---------------------------------------------------------------- offers and reviews

    @Test
    void onlyPublicActiveUnexhaustedCouponsAreAdvertised() throws Exception {
        coupon("WELCOME10", "PERCENT", "10", true, true, "NOW() - INTERVAL 1 DAY", "NOW() + INTERVAL 30 DAY", null, 0);
        coupon("SECRET50", "FLAT", "50", false, true, "NOW() - INTERVAL 1 DAY", "NOW() + INTERVAL 30 DAY", null, 0);
        coupon("EXPIRED", "FLAT", "50", true, true, "NOW() - INTERVAL 10 DAY", "NOW() - INTERVAL 1 DAY", null, 0);
        coupon("LATER", "FLAT", "50", true, true, "NOW() + INTERVAL 1 DAY", "NOW() + INTERVAL 9 DAY", null, 0);
        coupon("USEDUP", "FLAT", "50", true, true, "NOW() - INTERVAL 1 DAY", "NOW() + INTERVAL 9 DAY", 5, 5);
        coupon("OFF", "FLAT", "50", true, false, "NOW() - INTERVAL 1 DAY", "NOW() + INTERVAL 9 DAY", null, 0);

        mvc.perform(get("/api/offers"))
                .andExpect(jsonPath("$[*].code", contains("WELCOME10")))
                .andExpect(jsonPath("$[0].headline").value("10% off"));
    }

    @Test
    void reviewHighlightsAreApprovedPositiveAndAnonymised() throws Exception {
        long tees = id("SELECT id FROM categories WHERE code = 'TSHIRTS'");
        long productId = product(tees, "Review Tee", "review-tee", "599.00", null, false, false, 5);
        jdbc.update("INSERT INTO users (email, password_hash, full_name) VALUES ('rev@example.com', 'x', 'Ananya Sharma')");
        long userId = id("SELECT id FROM users WHERE email = 'rev@example.com'");
        review(userId, productId, 5, "Love it", "Great fabric and fit.", true);
        jdbc.update("INSERT INTO users (email, password_hash, full_name) VALUES ('rev2@example.com', 'x', 'Rohan Mehta')");
        long user2 = id("SELECT id FROM users WHERE email = 'rev2@example.com'");
        review(user2, productId, 2, "Meh", "Not for me at all.", true);
        jdbc.update("INSERT INTO users (email, password_hash, full_name) VALUES ('rev3@example.com', 'x', 'Priya Nair')");
        review(id("SELECT id FROM users WHERE email = 'rev3@example.com'"), productId, 5, "Pending", "Waiting for moderation.", false);

        mvc.perform(get("/api/reviews/highlights"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reviewerName").value("Ananya S."))
                .andExpect(jsonPath("$[0].productSlug").value("review-tee"))
                .andExpect(jsonPath("$[0].rating").value(5));
        mvc.perform(get("/api/home")).andExpect(jsonPath("$.reviews", hasSize(1)));
    }

    // ---------------------------------------------------------------- newsletter and contact

    @Test
    void newsletterSubscriptionIsIdempotent() throws Exception {
        subscribe("Fan@Example.com").andExpect(status().isAccepted());
        subscribe("fan@example.com").andExpect(status().isAccepted());
        assertThat(count("SELECT COUNT(*) FROM newsletter_subscribers WHERE email = 'fan@example.com'")).isEqualTo(1);

        jdbc.update("UPDATE newsletter_subscribers SET active = FALSE, unsubscribed_at = NOW()");
        subscribe("fan@example.com").andExpect(status().isAccepted());
        assertThat(count("SELECT COUNT(*) FROM newsletter_subscribers WHERE active = TRUE AND unsubscribed_at IS NULL"))
                .isEqualTo(1);

        subscribe("not-an-email").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void contactMessagesAreStoredAndBotsAreIgnored() throws Exception {
        contact("{\"name\":\"Asha Patil\",\"email\":\"Asha@Example.com\",\"phone\":\"9876543210\","
                + "\"subject\":\"Bulk order\",\"message\":\"Can you print 40 team T-Shirts by next month?\"}")
                .andExpect(status().isAccepted());
        assertThat(count("SELECT COUNT(*) FROM contact_messages WHERE email = 'asha@example.com' AND status = 'NEW'"))
                .isEqualTo(1);

        contact("{\"name\":\"Bot\",\"email\":\"bot@example.com\",\"subject\":\"Hello\","
                + "\"message\":\"Buy cheap things now please\",\"website\":\"http://spam.example\"}")
                .andExpect(status().isAccepted());
        assertThat(count("SELECT COUNT(*) FROM contact_messages WHERE email = 'bot@example.com'")).isZero();

        contact("{\"name\":\"A\",\"email\":\"x\",\"phone\":\"123\",\"subject\":\"\",\"message\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItems("name", "email", "phone", "subject", "message")));
    }

    @Test
    void contactFromLoggedInCustomerIsLinkedToTheAccount() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Asha Patil\",\"email\":\"asha@example.com\",\"password\":\"Secret123\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = body.substring(body.indexOf("\"accessToken\":\"") + 15, body.indexOf("\",", body.indexOf("\"accessToken\"")));

        mvc.perform(post("/api/contact").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha Patil\",\"email\":\"asha@example.com\",\"subject\":\"Order help\","
                                + "\"message\":\"Where can I see my order status?\"}"))
                .andExpect(status().isAccepted());
        assertThat(count("SELECT COUNT(*) FROM contact_messages m JOIN users u ON u.id = m.user_id "
                + "WHERE u.email = 'asha@example.com'")).isEqualTo(1);
    }

    // ---------------------------------------------------------------- helpers

    private org.springframework.test.web.servlet.ResultActions subscribe(String email) throws Exception {
        return mvc.perform(post("/api/newsletter").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions contact(String json) throws Exception {
        return mvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long product(long categoryId, String name, String slug, String base, String discount, boolean featured,
            boolean newArrival, int soldCount) {
        jdbc.update("INSERT INTO products (category_id, name, slug, base_price, discount_price, status, featured, "
                + "new_arrival, sold_count) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)",
                categoryId, name, slug, new java.math.BigDecimal(base),
                discount == null ? null : new java.math.BigDecimal(discount), featured, newArrival, soldCount);
        long id = id("SELECT id FROM products WHERE slug = '" + slug + "'");
        jdbc.update("INSERT INTO inventory (product_id, size_id, color_id, sku, quantity_on_hand) "
                + "SELECT ?, s.id, c.id, ?, 10 FROM product_sizes s, product_colors c "
                + "WHERE s.code = IF(? = (SELECT id FROM categories WHERE code = 'PANTS'), 'W32', 'M') AND c.code = 'BLACK'",
                id, "IT-" + id, categoryId);
        return id;
    }

    private void coupon(String code, String type, String value, boolean isPublic, boolean active, String from,
            String to, Integer usageLimit, int used) {
        jdbc.update("INSERT INTO coupons (code, description, discount_type, discount_value, valid_from, valid_to, "
                + "usage_limit, used_count, active, is_public) VALUES (?, 'Test', ?, ?, " + from + ", " + to + ", ?, ?, ?, ?)",
                code, type, new java.math.BigDecimal(value), usageLimit, used, active, isPublic);
    }

    private void review(long userId, long productId, int rating, String title, String comment, boolean approved) {
        jdbc.update("INSERT INTO reviews (user_id, product_id, rating, title, comment, approved) VALUES (?, ?, ?, ?, ?, ?)",
                userId, productId, rating, title, comment, approved);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
