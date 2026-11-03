package com.shop;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

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
 * Phase 7 catalog features against MySQL: filter facets, product reviews and the wishlist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;

    private long tees;
    private long pants;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
        tees = id("SELECT id FROM categories WHERE code = 'TSHIRTS'");
        pants = id("SELECT id FROM categories WHERE code = 'PANTS'");
    }

    @Test
    void facetsDescribeWhatIsInStock() throws Exception {
        long black = product(tees, "Black Tee", "black-tee", "499.00", null);
        variant(black, "M", "BLACK", 5);
        variant(black, "L", "BLACK", 0);
        long white = product(tees, "White Polo Tee", "white-polo-tee", "999.00", "799.00");
        variant(white, "M", "WHITE", 3);
        variant(white, "XL", "WHITE", 3);
        long chinos = product(pants, "Navy Chinos", "navy-chinos", "1499.00", null);
        variant(chinos, "W32", "NAVY", 2);

        mvc.perform(get("/api/products/facets").param("category", "t-shirts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minPrice").value(499.00))
                .andExpect(jsonPath("$.maxPrice").value(799.00))
                .andExpect(jsonPath("$.sizes[*].code", contains("M", "XL")))
                .andExpect(jsonPath("$.sizes[0].count").value(2))
                .andExpect(jsonPath("$.colors[*].code", contains("WHITE", "BLACK")));

        mvc.perform(get("/api/products/facets").param("q", "polo"))
                // "polo" also matches the seeded "Custom Polo T-Shirt".
                .andExpect(jsonPath("$.categories[*].code", contains("TSHIRTS", "CUSTOM_TSHIRTS")))
                .andExpect(jsonPath("$.categories[0].count").value(1))
                .andExpect(jsonPath("$.sizes[*].code", contains("M", "XL")));

        mvc.perform(get("/api/products/facets"))
                .andExpect(jsonPath("$.sizes[*].code", contains("M", "XL", "W32")))
                .andExpect(jsonPath("$.categories[*].code", contains("TSHIRTS", "PANTS", "CUSTOM_TSHIRTS")));

        mvc.perform(get("/api/products/facets").param("q", "nothing-matches-this"))
                .andExpect(jsonPath("$.minPrice").doesNotExist())
                .andExpect(jsonPath("$.sizes", hasSize(0)));
    }

    @Test
    void productReviewsArePagedAndOnlyApproved() throws Exception {
        long tee = product(tees, "Review Tee", "review-tee", "599.00", null);
        variant(tee, "M", "BLACK", 1);
        jdbc.update("INSERT INTO users (email, password_hash, full_name) VALUES ('a@example.com','x','Ananya Sharma'),"
                + " ('b@example.com','x','Rohan'), ('c@example.com','x','Priya Nair')");
        review("a@example.com", tee, 5, true);
        review("b@example.com", tee, 2, true);
        review("c@example.com", tee, 4, false);

        mvc.perform(get("/api/products/review-tee/reviews").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/products/review-tee/reviews"))
                .andExpect(jsonPath("$.content[*].reviewerName", org.hamcrest.Matchers.containsInAnyOrder("Ananya S.", "Rohan")));
        mvc.perform(get("/api/products/missing/reviews")).andExpect(status().isNotFound());
    }

    @Test
    void wishlistRequiresLoginAndIsIdempotent() throws Exception {
        long tee = product(tees, "Saved Tee", "saved-tee", "599.00", null);
        variant(tee, "M", "BLACK", 1);
        long other = product(tees, "Other Tee", "other-tee", "499.00", null);
        variant(other, "M", "WHITE", 1);
        jdbc.update("INSERT INTO products (category_id, name, slug, base_price, status) VALUES (?, 'Draft Tee', 'draft-tee', 1, 'DRAFT')", tees);
        long draft = id("SELECT id FROM products WHERE slug = 'draft-tee'");

        mvc.perform(get("/api/account/wishlist")).andExpect(status().isUnauthorized());

        String token = register();
        mvc.perform(put("/api/account/wishlist/" + tee).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(put("/api/account/wishlist/" + tee).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(put("/api/account/wishlist/" + other).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(put("/api/account/wishlist/" + draft).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        mvc.perform(put("/api/account/wishlist/999999").header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());

        mvc.perform(get("/api/account/wishlist/ids").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/account/wishlist").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[*].slug", org.hamcrest.Matchers.containsInAnyOrder("saved-tee", "other-tee")));

        // A product that becomes hidden disappears from the list but stays saved.
        jdbc.update("UPDATE products SET status = 'INACTIVE' WHERE id = ?", other);
        mvc.perform(get("/api/account/wishlist").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[*].slug", contains("saved-tee")));

        mvc.perform(delete("/api/account/wishlist/" + tee).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/account/wishlist/" + tee).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/account/wishlist").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------------------------------------------------------------- helpers

    private String register() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Kavya Rao\",\"email\":\"kavya@example.com\",\"password\":\"Secret123\"}"))
                .andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"accessToken\":\"") + 15;
        return body.substring(start, body.indexOf('"', start));
    }

    private long product(long categoryId, String name, String slug, String base, String discount) {
        jdbc.update("INSERT INTO products (category_id, name, slug, base_price, discount_price, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                categoryId, name, slug, new BigDecimal(base), discount == null ? null : new BigDecimal(discount));
        return id("SELECT id FROM products WHERE slug = '" + slug + "'");
    }

    private void variant(long productId, String size, String color, int quantity) {
        jdbc.update("INSERT INTO inventory (product_id, size_id, color_id, sku, quantity_on_hand) "
                + "SELECT ?, s.id, c.id, CONCAT('IT-', ?, '-', s.code, '-', c.code), ? FROM product_sizes s, product_colors c "
                + "WHERE s.code = ? AND c.code = ?", productId, productId, quantity, size, color);
    }

    private void review(String email, long productId, int rating, boolean approved) {
        jdbc.update("INSERT INTO reviews (user_id, product_id, rating, comment, approved) "
                + "SELECT id, ?, ?, 'Comment', ? FROM users WHERE email = ?", productId, rating, approved, email);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
