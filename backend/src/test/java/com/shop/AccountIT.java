package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;

import jakarta.servlet.http.Cookie;

/**
 * Customer portal (Phase 8) against MySQL: dashboard, profile, email and password
 * changes, and address management including ownership checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;

    private String token;
    private Cookie session;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
        MvcResult result = register("kavya@example.com", "9876543210");
        token = token(result);
        session = result.getResponse().getCookie("tshirt_rt");
    }

    // ---------------------------------------------------------------- dashboard

    @Test
    void dashboardStartsEmptyAndCountsOrders() throws Exception {
        mvc.perform(as(get("/api/account/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value("kavya@example.com"))
                .andExpect(jsonPath("$.orders.total").value(0))
                .andExpect(jsonPath("$.recentOrders", hasSize(0)))
                .andExpect(jsonPath("$.defaultAddress").doesNotExist());

        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'kavya@example.com'", Long.class);
        order(userId, "TS-1", "DELIVERED", "2026-10-01 10:00:00");
        order(userId, "TS-2", "SHIPPED", "2026-10-20 10:00:00");
        order(userId, "TS-3", "CANCELLED", "2026-10-25 10:00:00");
        long other = jdbc.queryForObject("SELECT id FROM users WHERE email = 'admin@test.local'", Long.class);
        order(other, "TS-OTHER", "DELIVERED", "2026-10-26 10:00:00");
        address("Home", true);

        mvc.perform(as(get("/api/account/dashboard")))
                .andExpect(jsonPath("$.orders.total").value(3))
                .andExpect(jsonPath("$.orders.active").value(1))
                .andExpect(jsonPath("$.orders.delivered").value(1))
                .andExpect(jsonPath("$.orders.cancelled").value(1))
                .andExpect(jsonPath("$.recentOrders[*].orderNumber", contains("TS-3", "TS-2", "TS-1")))
                .andExpect(jsonPath("$.recentOrders[0].grandTotal").value(478.00))
                .andExpect(jsonPath("$.addressCount").value(1))
                .andExpect(jsonPath("$.defaultAddress.label").value("Home"));

        mvc.perform(get("/api/account/dashboard")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- profile, email, password

    @Test
    void profileUpdatesNameAndPhoneWithUniqueness() throws Exception {
        register("other@example.com", "9123456780");
        mvc.perform(as(put("/api/account/profile")).content("{\"fullName\":\"Kavya R\",\"phone\":\"9123456780\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
        mvc.perform(as(put("/api/account/profile")).content("{\"fullName\":\"  Kavya Rao Iyer \",\"phone\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Kavya Rao Iyer"))
                .andExpect(jsonPath("$.phone").doesNotExist());
        mvc.perform(as(put("/api/account/profile")).content("{\"fullName\":\"K\",\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emailChangeNeedsCurrentPasswordAndAFreeAddress() throws Exception {
        mvc.perform(as(put("/api/account/email")).content("{\"newEmail\":\"new@example.com\",\"currentPassword\":\"Wrong1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        mvc.perform(as(put("/api/account/email")).content("{\"newEmail\":\"admin@test.local\",\"currentPassword\":\"Secret123\"}"))
                .andExpect(status().isConflict());
        mvc.perform(as(put("/api/account/email")).content("{\"newEmail\":\"New@Example.com\",\"currentPassword\":\"Secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false));
        login("new@example.com", "Secret123").andExpect(status().isOk());
        login("kavya@example.com", "Secret123").andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeSignsOutOtherDevicesButKeepsThisOne() throws Exception {
        Cookie otherDevice = login("kavya@example.com", "Secret123").andReturn().getResponse().getCookie("tshirt_rt");

        mvc.perform(as(put("/api/account/password")).content("{\"currentPassword\":\"Wrong1234\",\"newPassword\":\"NewSecret456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        mvc.perform(as(put("/api/account/password")).content("{\"currentPassword\":\"Secret123\",\"newPassword\":\"Secret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));

        MvcResult changed = mvc.perform(as(put("/api/account/password"))
                        .content("{\"currentPassword\":\"Secret123\",\"newPassword\":\"NewSecret456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().httpOnly("tshirt_rt", true))
                .andReturn();
        Cookie thisDevice = changed.getResponse().getCookie("tshirt_rt");

        mvc.perform(post("/api/auth/refresh").cookie(otherDevice)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(thisDevice)).andExpect(status().isOk());
        login("kavya@example.com", "NewSecret456").andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- addresses

    @Test
    void addressesKeepExactlyOneDefault() throws Exception {
        long home = address("Home", false);
        long work = address("Work", false);
        mvc.perform(as(get("/api/account/addresses")))
                .andExpect(jsonPath("$[*].label", contains("Home", "Work")))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].isDefault").value(false));

        mvc.perform(as(put("/api/account/addresses/" + work + "/default")))
                .andExpect(jsonPath("$.isDefault").value(true));
        mvc.perform(as(get("/api/account/addresses")))
                .andExpect(jsonPath("$[0].label").value("Work"))
                .andExpect(jsonPath("$[?(@.isDefault == true)]", hasSize(1)));

        long gym = address("Gym", true);
        mvc.perform(as(get("/api/account/addresses")))
                .andExpect(jsonPath("$[0].id").value((int) gym))
                .andExpect(jsonPath("$[?(@.isDefault == true)]", hasSize(1)));

        mvc.perform(as(delete("/api/account/addresses/" + gym))).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/account/addresses")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.isDefault == true)]", hasSize(1)));
        assertThat(home).isPositive();
    }

    /** Regression (Phase 18): a request that leaves out a true/false field is accepted (treated as false). */
    @Test
    void missingTrueFalseFieldsDefaultToFalse() throws Exception {
        mvc.perform(as(post("/api/account/addresses")).content("{\"fullName\":\"Asha Rao\",\"phone\":\"9876543210\","
                        + "\"line1\":\"1 Main Road\",\"city\":\"Pune\",\"state\":\"Maharashtra\",\"pincode\":\"411001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true)); // first address becomes the default anyway
        mvc.perform(as(post("/api/account/addresses")).content("{\"fullName\":\"Asha Rao\",\"phone\":\"9876543210\","
                        + "\"line1\":\"2 Side Road\",\"city\":\"Pune\",\"state\":\"Maharashtra\",\"pincode\":\"411001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(false));
        // A missing required number still fails validation instead of silently becoming 0
        mvc.perform(as(post("/api/cart/items")).content("{\"sku\":\"ANY\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void addressValidationEditingAndOwnership() throws Exception {
        long id = address("Home", false);
        mvc.perform(as(put("/api/account/addresses/" + id)).content(addressJson("Home", "Pune", "maharashtra", "411002", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pincode").value("411002"))
                .andExpect(jsonPath("$.state").value("Maharashtra"));
        mvc.perform(as(post("/api/account/addresses")).content(addressJson("X", "Pune", "Atlantis", "411001", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mvc.perform(as(post("/api/account/addresses")).content(addressJson("X", "Pune", "Goa", "012345", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String intruder = token(register("intruder@example.com", null));
        mvc.perform(put("/api/account/addresses/" + id).header("Authorization", "Bearer " + intruder)
                        .contentType(MediaType.APPLICATION_JSON).content(addressJson("Mine", "Pune", "Goa", "411001", false)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/account/addresses/" + id).header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/account/addresses"))).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(as(get("/api/account/states"))).andExpect(jsonPath("$", hasSize(36)));
    }

    // ---------------------------------------------------------------- helpers

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private MvcResult register(String email, String phone) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Kavya Rao\",\"email\":\"" + email + "\",\"password\":\"Secret123\""
                                + (phone == null ? "" : ",\"phone\":\"" + phone + "\"") + "}"))
                .andExpect(status().isCreated()).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private long address(String label, boolean makeDefault) throws Exception {
        String body = mvc.perform(as(post("/api/account/addresses"))
                        .content(addressJson(label, "Pune", "Maharashtra", "411001", makeDefault)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.substring(body.indexOf("\"id\":") + 5, body.indexOf(',')));
    }

    private static String addressJson(String label, String city, String state, String pincode, boolean makeDefault) {
        return "{\"label\":\"" + label + "\",\"fullName\":\"Kavya Rao\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\","
                + "\"city\":\"" + city + "\",\"state\":\"" + state + "\",\"pincode\":\"" + pincode + "\","
                + "\"makeDefault\":" + makeDefault + "}";
    }

    private void order(long userId, String number, String status, String placedAt) {
        jdbc.update("INSERT INTO orders (order_number, user_id, order_status, payment_status, payment_method, subtotal, "
                + "customization_total, shipping_charge, discount_total, grand_total, contact_name, contact_email, "
                + "contact_phone, shipping_address, placed_at) VALUES (?, ?, ?, 'SUCCESS', 'RAZORPAY', 299, 130, 49, 0, "
                + "478, 'Kavya', 'k@example.com', '9876543210', JSON_OBJECT(), ?)", number, userId, status, placedAt);
    }

    private static String token(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        int start = json.indexOf("\"accessToken\":\"") + 15;
        return json.substring(start, json.indexOf('"', start));
    }
}
