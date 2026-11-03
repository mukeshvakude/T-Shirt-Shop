package com.shop;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;

/**
 * Shopping cart (Phase 11) against MySQL: server prices, stock checks, variant changes, custom
 * designs in several sizes, coupons, guest quotes and merging the guest bag.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartIT {

    private static final String M_BLACK = "CART-TEE-BLK-M";
    private static final String L_BLACK = "CART-TEE-BLK-L";
    private static final String M_WHITE = "CART-TEE-WHT-M";
    private static final String CUSTOM_SKUS = "(select sku from (select i.sku from inventory i join products p on p.id = i.product_id "
            + "join product_colors c on c.id = i.color_id join product_sizes s on s.id = i.size_id "
            + "where p.slug = 'custom-round-neck-t-shirt' and c.code = 'BLACK' and s.code in ('M','L')) x)";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;

    private String customer;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
        jdbc.update("""
                INSERT INTO products (category_id, name, slug, base_price, discount_price, status)
                SELECT id, 'Cart Test Tee', 'cart-test-tee', 599.00, 499.00, 'ACTIVE' FROM categories WHERE code = 'TSHIRTS'""");
        variant(M_BLACK, "M", "BLACK", 10);
        variant(L_BLACK, "L", "BLACK", 1);
        variant(M_WHITE, "M", "WHITE", 0);
        jdbc.update("UPDATE inventory SET quantity_on_hand = 30 WHERE sku IN " + CUSTOM_SKUS);
        jdbc.update("""
                INSERT INTO coupons (code, description, discount_type, discount_value, min_order_amount, max_discount, valid_from, valid_to, usage_limit, per_user_limit) VALUES
                ('WELCOME10', '10% off (max INR 200)', 'PERCENT', 10, 499, 200, '2020-01-01', '2099-01-01', NULL, 1),
                ('FLAT100', 'INR 100 off above INR 999', 'FLAT', 100, 999, NULL, '2020-01-01', '2099-01-01', 1000, 3),
                ('OLD50', 'Expired', 'FLAT', 50, 0, NULL, '2020-01-01', '2021-01-01', NULL, 1)""");
        customer = register("meera@example.com");
    }

    @AfterEach
    void restoreCustomStock() {
        jdbc.update("UPDATE inventory SET quantity_on_hand = 0 WHERE sku IN " + CUSTOM_SKUS);
    }

    @Test
    void guestsGetServerPricesWithoutSigningIn() throws Exception {
        mvc.perform(post("/api/cart/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sku\":\"" + M_BLACK + "\",\"quantity\":2},{\"sku\":\"NO-SUCH-SKU\",\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].key").value(M_BLACK))
                .andExpect(jsonPath("$.items[0].unitPrice").value(499.00))
                .andExpect(jsonPath("$.items[0].unitMrp").value(599.00))
                .andExpect(jsonPath("$.subtotal").value(998.00))
                .andExpect(jsonPath("$.shipping").value(49.00))
                .andExpect(jsonPath("$.total").value(1047.00))
                .andExpect(jsonPath("$.notices", hasSize(1)))
                .andExpect(jsonPath("$.canCheckout").value(true));
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    }

    @Test
    void addChangeAndRemoveProducts() throws Exception {
        add("{\"sku\":\"" + M_BLACK + "\",\"quantity\":2}").andExpect(jsonPath("$.itemCount").value(2));
        String id = String.valueOf(add("{\"sku\":\"" + M_BLACK + "\",\"quantity\":1}")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].maxQuantity").value(10))
                .andExpect(jsonPath("$.items[0].variants", hasSize(3)))
                .andReturn().getResponse().getContentAsString().replaceAll("(?s).*\"items\":\\[\\{\"id\":(\\d+).*", "$1"));

        patchItem(id, "{\"quantity\":5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lineTotal").value(2495.00))
                .andExpect(jsonPath("$.shipping").value(0.00));
        patchItem(id, "{\"quantity\":11}").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("NOT_ENOUGH_STOCK"));
        patchItem(id, "{\"quantity\":21}").andExpect(status().isBadRequest());
        patchItem(id, "{\"colorCode\":\"WHITE\"}").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
        patchItem(id, "{\"sizeCode\":\"L\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Only 1 left in this size and colour."));
        patchItem(id, "{\"sizeCode\":\"L\",\"quantity\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sku").value(L_BLACK))
                .andExpect(jsonPath("$.items[0].status").value("LOW_STOCK"));
        patchItem(id, "{\"sizeCode\":\"XXL\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VARIANT_UNAVAILABLE"));

        // All stock of L is in the bag already
        add("{\"sku\":\"" + L_BLACK + "\",\"quantity\":1}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("QUANTITY_LIMIT"));
        add("{\"sku\":\"" + M_WHITE + "\",\"quantity\":1}").andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
        String blank = jdbc.queryForObject("select i.sku from inventory i join products p on p.id = i.product_id "
                + "where p.slug = 'custom-round-neck-t-shirt' limit 1", String.class);
        add("{\"sku\":\"" + blank + "\",\"quantity\":1}").andExpect(jsonPath("$.code").value("DESIGN_REQUIRED"));

        mvc.perform(as(delete("/api/cart/items/" + id))).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.shipping").value(0.00)).andExpect(jsonPath("$.canCheckout").value(false));
        mvc.perform(as(delete("/api/cart/items/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void customDesignsInSeveralSizes() throws Exception {
        Long userId = jdbc.queryForObject("select id from users where email = 'meera@example.com'", Long.class);
        jdbc.update("""
                INSERT INTO custom_designs (design_code, user_id, base_product_id, name, tshirt_type, color_code, size_code, placements, text_items)
                SELECT 'DSN-CARTTEST', ?, id, 'Team tee', 'ROUND_NECK', 'BLACK', 'M', JSON_ARRAY('FRONT'),
                       JSON_ARRAY(JSON_OBJECT('side','FRONT','text','Go team','font','Anton','size',40,'color','#FFFFFF'))
                FROM products WHERE slug = 'custom-round-neck-t-shirt'""", userId);

        add("{\"designCode\":\"DSN-CARTTEST\",\"quantity\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("CUSTOM"))
                .andExpect(jsonPath("$.items[0].sizeCode").value("M"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(379.00)) // 299 + 50 front + 30 text
                .andExpect(jsonPath("$.items[0].design.texts[0]").value("Front: Go team (Anton)"))
                .andExpect(jsonPath("$.items[0].design.charges", hasSize(2)))
                .andExpect(jsonPath("$.items[0].variants", hasSize(5))); // sizes of the design colour only
        String body = add("{\"designCode\":\"DSN-CARTTEST\",\"sizeCode\":\"L\",\"quantity\":1}")
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.subtotal").value(1137.00))
                .andReturn().getResponse().getContentAsString();
        String mId = body.replaceAll("(?s).*\"items\":\\[\\{\"id\":(\\d+).*", "$1");

        patchItem(mId, "{\"colorCode\":\"WHITE\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COLOR_FIXED"));
        patchItem(mId, "{\"sizeCode\":\"L\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        String other = register("other@example.com");
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designCode\":\"DSN-CARTTEST\",\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void couponsAreCheckedOnTheServer() throws Exception {
        String id = add("{\"sku\":\"" + M_BLACK + "\",\"quantity\":1}").andReturn().getResponse().getContentAsString()
                .replaceAll("(?s).*\"items\":\\[\\{\"id\":(\\d+).*", "$1");
        coupon("welcome10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupon.code").value("WELCOME10"))
                .andExpect(jsonPath("$.coupon.applied").value(true))
                .andExpect(jsonPath("$.discount").value(49.90))
                .andExpect(jsonPath("$.total").value(498.10)); // 499 - 49.90 + 49 shipping
        coupon("FLAT100").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("COUPON_MIN_ORDER"))
                .andExpect(jsonPath("$.detail").value(containsString("Add ₹500 more")));
        coupon("OLD50").andExpect(jsonPath("$.code").value("COUPON_EXPIRED"));
        coupon("NOPE").andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));

        patchItem(id, "{\"quantity\":3}");
        coupon("FLAT100").andExpect(jsonPath("$.discount").value(100.00)).andExpect(jsonPath("$.total").value(1397.00));

        // Dropping below the minimum keeps the code but stops the discount, with a reason
        patchItem(id, "{\"quantity\":1}")
                .andExpect(jsonPath("$.coupon.applied").value(false))
                .andExpect(jsonPath("$.coupon.message").value(containsString("more to use FLAT100")))
                .andExpect(jsonPath("$.discount").value(0.00));
        mvc.perform(as(delete("/api/cart/coupon"))).andExpect(jsonPath("$.coupon").doesNotExist());
    }

    @Test
    void stockAndAvailabilityChangesBlockCheckout() throws Exception {
        add("{\"sku\":\"" + M_BLACK + "\",\"quantity\":3}").andExpect(jsonPath("$.canCheckout").value(true));

        jdbc.update("UPDATE inventory SET quantity_on_hand = 2 WHERE sku = ?", M_BLACK);
        mvc.perform(as(get("/api/cart")))
                .andExpect(jsonPath("$.items[0].status").value("NOT_ENOUGH_STOCK"))
                .andExpect(jsonPath("$.subtotal").value(0.00))
                .andExpect(jsonPath("$.canCheckout").value(false));
        jdbc.update("UPDATE inventory SET quantity_on_hand = 0 WHERE sku = ?", M_BLACK);
        mvc.perform(as(get("/api/cart"))).andExpect(jsonPath("$.items[0].status").value("OUT_OF_STOCK"));
        jdbc.update("UPDATE inventory SET quantity_on_hand = 10 WHERE sku = ?", M_BLACK);
        jdbc.update("UPDATE products SET status = 'INACTIVE' WHERE slug = 'cart-test-tee'");
        mvc.perform(as(get("/api/cart"))).andExpect(jsonPath("$.items[0].status").value("UNAVAILABLE"));
        jdbc.update("UPDATE products SET status = 'ACTIVE', discount_price = 449.00 WHERE slug = 'cart-test-tee'");
        mvc.perform(as(get("/api/cart")))
                .andExpect(jsonPath("$.items[0].unitPrice").value(449.00)) // prices are always current
                .andExpect(jsonPath("$.canCheckout").value(true));
    }

    @Test
    void guestBagMergesAfterSignIn() throws Exception {
        add("{\"sku\":\"" + M_BLACK + "\",\"quantity\":1}");
        mvc.perform(as(post("/api/cart/merge")).content("{\"items\":[{\"sku\":\"" + M_BLACK + "\",\"quantity\":2},"
                        + "{\"sku\":\"" + L_BLACK + "\",\"quantity\":3},{\"sku\":\"GONE-1\",\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[1].quantity").value(1))
                .andExpect(jsonPath("$.notices", hasSize(2)));
    }

    // ---------------------------------------------------------------- helpers

    private void variant(String sku, String size, String color, int stock) {
        jdbc.update("""
                INSERT INTO inventory (product_id, size_id, color_id, sku, quantity_on_hand, low_stock_threshold)
                SELECT p.id, s.id, c.id, ?, ?, 2 FROM products p, product_sizes s, product_colors c
                WHERE p.slug = 'cart-test-tee' AND s.code = ? AND s.size_group = 'APPAREL' AND c.code = ?""", sku, stock, size, color);
    }

    private ResultActions add(String body) throws Exception {
        return mvc.perform(as(post("/api/cart/items")).content(body));
    }

    private ResultActions patchItem(String id, String body) throws Exception {
        return mvc.perform(as(patch("/api/cart/items/" + id)).content(body));
    }

    private ResultActions coupon(String code) throws Exception {
        return mvc.perform(as(put("/api/cart/coupon")).content("{\"code\":\"" + code + "\"}"));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON);
    }

    private String register(String email) throws Exception {
        String json = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Meera Iyer\",\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int start = json.indexOf("\"accessToken\":\"") + 15;
        return json.substring(start, json.indexOf('"', start));
    }
}
