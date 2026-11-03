package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.shop.jobs.OrderExpiryJob;
import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;

/**
 * Checkout (Phase 12) against MySQL: server totals, Cash on Delivery orders, online orders with a
 * stock hold that expires, unpaid order cancellation, coupons, and the guard rails.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutIT {

    private static final String M_BLACK = "CHK-TEE-BLK-M";
    private static final String L_BLACK = "CHK-TEE-BLK-L";
    private static final String CUSTOM_SKUS = "(select sku from (select i.sku from inventory i join products p on p.id = i.product_id "
            + "join product_colors c on c.id = i.color_id join product_sizes s on s.id = i.size_id "
            + "where p.slug = 'custom-round-neck-t-shirt' and c.code = 'BLACK' and s.code = 'M') x)";
    private static final String ADDRESS = "{\"fullName\":\"Meera Iyer\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\","
            + "\"landmark\":\"Near the park\",\"city\":\"Pune\",\"state\":\"maharashtra\",\"pincode\":\"411001\",\"makeDefault\":false}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;
    @Autowired
    private OrderExpiryJob expiryJob;

    private String customer;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
        jdbc.update("""
                INSERT INTO products (category_id, name, slug, base_price, discount_price, status)
                SELECT id, 'Checkout Tee', 'checkout-tee', 599.00, 499.00, 'ACTIVE' FROM categories WHERE code = 'TSHIRTS'""");
        variant(M_BLACK, "M", 10);
        variant(L_BLACK, "L", 1);
        jdbc.update("UPDATE inventory SET quantity_on_hand = 30, quantity_reserved = 0 WHERE sku IN " + CUSTOM_SKUS);
        jdbc.update("""
                INSERT INTO coupons (code, description, discount_type, discount_value, min_order_amount, max_discount, valid_from, valid_to, usage_limit, per_user_limit) VALUES
                ('WELCOME10', '10% off', 'PERCENT', 10, 499, 200, '2020-01-01', '2099-01-01', NULL, 1),
                ('FLAT100', 'INR 100 off', 'FLAT', 100, 999, NULL, '2020-01-01', '2099-01-01', 1000, 3)""");
        customer = register("meera@example.com");
        userId = jdbc.queryForObject("select id from users where email = 'meera@example.com'", Long.class);
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE inventory SET quantity_on_hand = 0, quantity_reserved = 0 WHERE sku IN " + CUSTOM_SKUS);
        jdbc.update("UPDATE app_settings SET setting_value = '5000.00' WHERE setting_key = 'cod.max_order_value'");
    }

    @Test
    void cashOnDeliveryOrderIsConfirmedAndTakesTheStock() throws Exception {
        add("{\"sku\":\"" + M_BLACK + "\",\"quantity\":2}");
        mvc.perform(as(put("/api/cart/coupon")).content("{\"code\":\"WELCOME10\"}")).andExpect(status().isOk());

        mvc.perform(as(get("/api/checkout")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.itemsTotal").value(998.00))
                .andExpect(jsonPath("$.totals.customizationTotal").value(0.00))
                .andExpect(jsonPath("$.totals.discount").value(99.80))
                .andExpect(jsonPath("$.totals.shipping").value(49.00))
                .andExpect(jsonPath("$.totals.total").value(947.20))
                .andExpect(jsonPath("$.contact.email").value("meera@example.com"))
                .andExpect(jsonPath("$.paymentOptions[?(@.method=='COD')].available").value(true))
                .andExpect(jsonPath("$.paymentOptions[?(@.method=='RAZORPAY')].available").value(true))
                .andExpect(jsonPath("$.reservationMinutes").value(15));

        String number = field(place("COD", null, ADDRESS, true, "947.20")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber", matchesPattern("^TS-\\d{8}-\\d{6}$")))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.awaitingPayment").value(false))
                .andExpect(jsonPath("$.items[0].unitBasePrice").value(499.00))
                .andExpect(jsonPath("$.items[0].colorName").value("Black"))
                .andExpect(jsonPath("$.couponCode").value("WELCOME10"))
                .andExpect(jsonPath("$.total").value(947.20))
                .andExpect(jsonPath("$.shippingAddress.state").value("Maharashtra"))
                .andExpect(jsonPath("$.history[0].status").value("CONFIRMED")), "orderNumber");

        assertThat(stock(M_BLACK)).containsExactly(8, 0);
        assertThat(jdbc.queryForObject("select status from inventory_reservations", String.class)).isEqualTo("COMMITTED");
        assertThat(jdbc.queryForObject("select used_count from coupons where code = 'WELCOME10'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from addresses where user_id = ?", Integer.class, userId)).isEqualTo(1);
        mvc.perform(as(get("/api/cart"))).andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.coupon").doesNotExist());

        mvc.perform(as(get("/api/orders/" + number))).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)));
        String other = register("other@example.com");
        mvc.perform(get("/api/orders/" + number).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());

        // Placing again: the bag is empty; the coupon cannot be used twice
        place("COD", null, ADDRESS, false, "947.20").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
        add("{\"sku\":\"" + M_BLACK + "\",\"quantity\":1}");
        mvc.perform(as(put("/api/cart/coupon")).content("{\"code\":\"WELCOME10\"}"))
                .andExpect(jsonPath("$.code").value("COUPON_ALREADY_USED"));
    }

    @Test
    void onlineOrderHoldsStockUntilThePaymentWindowEnds() throws Exception {
        jdbc.update("""
                INSERT INTO custom_designs (design_code, user_id, base_product_id, name, tshirt_type, color_code, size_code, placements, text_items)
                SELECT 'DSN-CHECKOUT', ?, id, 'Team tee', 'ROUND_NECK', 'BLACK', 'M', JSON_ARRAY('FRONT'),
                       JSON_ARRAY(JSON_OBJECT('side','FRONT','text','Go','font','Anton','size',40,'color','#FFFFFF'))
                FROM products WHERE slug = 'custom-round-neck-t-shirt'""", userId);
        add("{\"designCode\":\"DSN-CHECKOUT\",\"quantity\":2}");
        add("{\"sku\":\"" + L_BLACK + "\",\"quantity\":1}");
        mvc.perform(as(put("/api/cart/coupon")).content("{\"code\":\"FLAT100\"}")).andExpect(status().isOk());

        // 2 x 379 (299 + 50 front + 30 text) + 499 = 1257, free shipping, minus 100
        mvc.perform(as(get("/api/checkout")))
                .andExpect(jsonPath("$.totals.itemsTotal").value(1097.00))
                .andExpect(jsonPath("$.totals.customizationTotal").value(160.00))
                .andExpect(jsonPath("$.totals.shipping").value(0.00))
                .andExpect(jsonPath("$.totals.total").value(1157.00));

        String number = field(place("RAZORPAY", null, ADDRESS, false, "1157")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("CREATED"))
                .andExpect(jsonPath("$.awaitingPayment").value(true))
                .andExpect(jsonPath("$.reservationExpiresAt", notNullValue()))
                .andExpect(jsonPath("$.items[0].designCode").value("DSN-CHECKOUT"))
                .andExpect(jsonPath("$.items[0].unitBasePrice").value(299.00))
                .andExpect(jsonPath("$.items[0].unitCustomizationCharge").value(80.00))
                .andExpect(jsonPath("$.items[0].charges", hasSize(2))), "orderNumber");

        assertThat(stock(L_BLACK)).containsExactly(1, 1); // held, not sold
        assertThat(jdbc.queryForObject("select locked from custom_designs where design_code = 'DSN-CHECKOUT'", Boolean.class)).isTrue();
        // Nobody else can buy the held item meanwhile
        String other = register("other@example.com");
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + L_BLACK + "\",\"quantity\":1}")).andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));

        // The job ignores holds that are still running
        expiryJob.run();
        assertThat(stock(L_BLACK)).containsExactly(1, 1);

        // Payment window ends: stock goes back, the order stays open
        jdbc.update("UPDATE inventory_reservations SET expires_at = DATE_SUB(expires_at, INTERVAL 1 DAY)");
        expiryJob.run();
        assertThat(stock(L_BLACK)).containsExactly(1, 0);
        assertThat(jdbc.queryForList("select distinct status from inventory_reservations", String.class)).containsExactly("EXPIRED");
        mvc.perform(as(get("/api/orders/" + number)))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"));

        // Still unpaid a day later: cancelled, coupon use and design lock given back
        jdbc.update("UPDATE orders SET placed_at = DATE_SUB(placed_at, INTERVAL 25 HOUR)");
        expiryJob.run();
        mvc.perform(as(get("/api/orders/" + number)))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Not paid within 24 hours"))
                .andExpect(jsonPath("$.history", hasSize(3)));
        assertThat(jdbc.queryForObject("select used_count from coupons where code = 'FLAT100'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from coupon_redemptions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select locked from custom_designs where design_code = 'DSN-CHECKOUT'", Boolean.class)).isFalse();
    }

    @Test
    void guardRails() throws Exception {
        mvc.perform(post("/api/checkout/place-order").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        place("COD", null, ADDRESS, false, "0").andExpect(jsonPath("$.code").value("CART_EMPTY"));

        add("{\"sku\":\"" + M_BLACK + "\",\"quantity\":3}"); // 1497, free shipping
        place("COD", null, ADDRESS, false, "1400.00").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRICE_CHANGED"));
        place("COD", null, null, false, "1497").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADDRESS_REQUIRED"));
        place("COD", 999999L, null, false, "1497").andExpect(status().isNotFound());
        place("COD", null, ADDRESS.replace("411001", "11001"), false, "1497").andExpect(status().isBadRequest());
        place("CARD", null, ADDRESS, false, "1497").andExpect(status().isBadRequest());

        jdbc.update("UPDATE app_settings SET setting_value = '1000.00' WHERE setting_key = 'cod.max_order_value'");
        place("COD", null, ADDRESS, false, "1497").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COD_UNAVAILABLE"));

        jdbc.update("UPDATE inventory SET quantity_on_hand = 2 WHERE sku = ?", M_BLACK);
        place("RAZORPAY", null, ADDRESS, false, "1497").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_NOT_READY"));
        assertThat(jdbc.queryForObject("select count(*) from orders", Integer.class)).isZero();

        // Saved address works too
        jdbc.update("UPDATE inventory SET quantity_on_hand = 10 WHERE sku = ?", M_BLACK);
        Long addressId = Long.valueOf(field(mvc.perform(as(post("/api/account/addresses")).content(ADDRESS)).andExpect(status().isCreated()), "id"));
        place("RAZORPAY", addressId, null, false, "1497").andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingAddress.city").value("Pune"));
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions place(String method, Long addressId, String address, boolean save, String expectedTotal) throws Exception {
        String body = "{\"contact\":{\"name\":\"Meera Iyer\",\"email\":\"meera@example.com\",\"phone\":\"9876543210\"},"
                + (addressId != null ? "\"addressId\":" + addressId + "," : "")
                + (address != null ? "\"address\":" + address + "," : "")
                + "\"saveAddress\":" + save + ",\"paymentMethod\":\"" + method + "\",\"expectedTotal\":" + expectedTotal + "}";
        return mvc.perform(as(post("/api/checkout/place-order")).content(body));
    }

    private void add(String body) throws Exception {
        mvc.perform(as(post("/api/cart/items")).content(body)).andExpect(status().isOk());
    }

    private Integer[] stock(String sku) {
        return jdbc.queryForObject("select quantity_on_hand, quantity_reserved from inventory where sku = ?",
                (rs, n) -> new Integer[] {rs.getInt(1), rs.getInt(2)}, sku);
    }

    private void variant(String sku, String size, int stock) {
        jdbc.update("""
                INSERT INTO inventory (product_id, size_id, color_id, sku, quantity_on_hand, low_stock_threshold)
                SELECT p.id, s.id, c.id, ?, ?, 0 FROM products p, product_sizes s, product_colors c
                WHERE p.slug = 'checkout-tee' AND s.code = ? AND s.size_group = 'APPAREL' AND c.code = 'BLACK'""", sku, stock, size);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON);
    }

    private String register(String email) throws Exception {
        return field(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Meera Iyer\",\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isCreated()), "accessToken");
    }

    private static String field(ResultActions result, String name) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        String key = "\"" + name + "\":";
        int start = json.indexOf(key) + key.length();
        if (json.charAt(start) == '"') {
            return json.substring(start + 1, json.indexOf('"', start + 1));
        }
        int end = start;
        while (",}".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(start, end);
    }
}
