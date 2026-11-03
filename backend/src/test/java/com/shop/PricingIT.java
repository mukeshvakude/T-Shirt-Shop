package com.shop;

import static org.hamcrest.Matchers.hasSize;
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

import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;

/**
 * Dynamic pricing (Phase 10): server-side estimates from the seeded charges, validation, and
 * admin changes taking effect immediately.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PricingIT {

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

    /** Charges and shipping are shared reference data, so restore the seeded values. */
    @AfterEach
    void restoreSeed() {
        jdbc.update("DELETE FROM customization_charges WHERE charge_type = 'SIZE' AND code = 'L'");
        jdbc.update("UPDATE customization_charges SET amount = 50.00, active = TRUE WHERE charge_type = 'PRINT_PLACEMENT' AND code = 'FRONT'");
        jdbc.update("UPDATE app_settings SET setting_value = '49.00' WHERE setting_key = 'shipping.flat_charge'");
        jdbc.update("UPDATE app_settings SET setting_value = '999.00' WHERE setting_key = 'shipping.free_threshold'");
    }

    @Test
    void publicPriceListShowsChargesAndShipping() throws Exception {
        mvc.perform(get("/api/pricing/charges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges[?(@.type=='PRINT_PLACEMENT' && @.code=='FULL_FRONT')].amount").value(90.00))
                .andExpect(jsonPath("$.charges[?(@.type=='SIZE' && @.code=='XXL')].amount").value(40.00))
                .andExpect(jsonPath("$.charges[?(@.code=='WHITE')]", hasSize(0))) // zero charges are left out
                .andExpect(jsonPath("$.shippingFlatCharge").value(49.00))
                .andExpect(jsonPath("$.freeShippingThreshold").value(999.00))
                .andExpect(jsonPath("$.maxQuantityPerItem").value(20));
    }

    @Test
    void simpleFrontPrint() throws Exception {
        estimate("ROUND_NECK", "WHITE", "M", "[\"FRONT\"]", false, false, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.basePrice").value(299.00))
                .andExpect(jsonPath("$.charges", hasSize(1)))
                .andExpect(jsonPath("$.charges[0].label").value("Front print"))
                .andExpect(jsonPath("$.unitPrice").value(349.00))
                .andExpect(jsonPath("$.subtotal").value(349.00))
                .andExpect(jsonPath("$.shippingEstimate").value(49.00))
                .andExpect(jsonPath("$.amountToFreeShipping").value(650.00))
                .andExpect(jsonPath("$.taxInclusive").value(true));
    }

    @Test
    void everyChargeAddsUp() throws Exception {
        // 299 base + 60 Polo + 90 Full Front + 50 Back + 30 text + 100 premium + 40 XXL = 669
        estimate("POLO", "BLACK", "XXL", "[\"FULL_FRONT\",\"BACK\"]", true, true, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges", hasSize(6)))
                .andExpect(jsonPath("$.customizationPerUnit").value(370.00))
                .andExpect(jsonPath("$.unitPrice").value(669.00))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.subtotal").value(1338.00))
                .andExpect(jsonPath("$.shippingEstimate").value(0.00))
                .andExpect(jsonPath("$.amountToFreeShipping").value(0.00));

        // No print yet: blank T-Shirt price plus the type charge
        estimate("OVERSIZED", "BLACK", "M", "[]", false, false, null)
                .andExpect(jsonPath("$.unitPrice").value(379.00))
                .andExpect(jsonPath("$.charges[0].label").value("Oversized style"));
    }

    @Test
    void rejectsInvalidChoices() throws Exception {
        estimate("ROUND_NECK", "WHITE", "M", "[\"FRONT\",\"FULL_FRONT\"]", false, false, 1)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLACEMENT_CONFLICT"));
        estimate("HOODIE", "WHITE", "M", "[]", false, false, 1)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_TSHIRT_TYPE"));
        estimate("ROUND_NECK", "MAROON", "M", "[]", false, false, 1)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VARIANT_UNAVAILABLE"));
        estimate("ROUND_NECK", "WHITE", "M", "[\"SLEEVE\"]", false, false, 1)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_PLACEMENT"));
        estimate("ROUND_NECK", "WHITE", "M", "[]", false, false, 21)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        estimate("ROUND_NECK", "WHITE", "M", "[]", false, false, 0).andExpect(status().isBadRequest());
    }

    @Test
    void adminChangesApplyImmediately() throws Exception {
        String admin = login("admin@test.local", "TestAdmin123");
        String customer = register("asha@example.com");

        mvc.perform(get("/api/admin/pricing/charges").header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/pricing/charges").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SIZE\",\"code\":\"L\",\"amount\":25,\"active\":true}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/pricing/charges").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='SIZE' && @.code=='L')].amount").value(0.00))
                .andExpect(jsonPath("$[?(@.type=='SIZE' && @.code=='L')].active").value(false))
                .andExpect(jsonPath("$[?(@.type=='COLOR' && @.code=='SKY_BLUE')].label").value("Sky Blue"))
                .andExpect(jsonPath("$[?(@.type=='PRINT_PLACEMENT' && @.code=='FRONT')].amount").value(50.00));

        // New charge for size L
        mvc.perform(put("/api/admin/pricing/charges").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SIZE\",\"code\":\"L\",\"amount\":25,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.description").value("L charge"));
        // Front print switched off
        mvc.perform(put("/api/admin/pricing/charges").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PRINT_PLACEMENT\",\"code\":\"FRONT\",\"amount\":50,\"active\":false}"))
                .andExpect(status().isOk());
        estimate("ROUND_NECK", "WHITE", "L", "[\"FRONT\"]", false, false, 1)
                .andExpect(jsonPath("$.charges", hasSize(1)))
                .andExpect(jsonPath("$.charges[0].label").value("Size L"))
                .andExpect(jsonPath("$.unitPrice").value(324.00));

        mvc.perform(put("/api/admin/pricing/charges").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SIZE\",\"code\":\"XS9\",\"amount\":5,\"active\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_CHARGE"));
        mvc.perform(put("/api/admin/pricing/charges").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SIZE\",\"code\":\"L\",\"amount\":-1,\"active\":true}"))
                .andExpect(status().isBadRequest());

        // Shipping
        mvc.perform(put("/api/admin/pricing/shipping").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flatCharge\":59,\"freeThreshold\":250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flatCharge").value(59.00));
        estimate("ROUND_NECK", "WHITE", "M", "[]", false, false, 1)
                .andExpect(jsonPath("$.shippingEstimate").value(0.00))
                .andExpect(jsonPath("$.freeShippingThreshold").value(250.00));
        mvc.perform(get("/api/admin/pricing/shipping").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.freeThreshold").value(250.00));
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions estimate(String type, String color, String size, String placements, boolean text, boolean premium,
            Integer quantity) throws Exception {
        String body = "{\"tshirtType\":\"" + type + "\",\"colorCode\":\"" + color + "\",\"sizeCode\":\"" + size + "\","
                + "\"placements\":" + placements + ",\"hasText\":" + text + ",\"premiumPrint\":" + premium
                + (quantity == null ? "" : ",\"quantity\":" + quantity) + "}";
        return mvc.perform(post("/api/pricing/design-estimate").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String register(String email) throws Exception {
        return token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Asha Rao\",\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        return token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String token(String json) {
        int start = json.indexOf("\"accessToken\":\"") + 15;
        return json.substring(start, json.indexOf('"', start));
    }
}
