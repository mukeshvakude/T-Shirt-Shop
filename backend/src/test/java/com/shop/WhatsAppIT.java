package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.shop.notification.WhatsAppMessages;
import com.shop.notification.WhatsAppSender;
import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;

/** WhatsApp: chat number, customer opt-in, automatic updates (sender mocked) and admin one-click links. */
@SpringBootTest(properties = {"app.whatsapp.chat-number=+91 98765 00000", "app.whatsapp.owner-number=09876500001",
        "app.frontend-url=https://shop.test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WhatsAppIT {

    private static final String SKU = "WA-TEE-BLK-M";
    private static final String ADDRESS = "{\"fullName\":\"Neha S\",\"phone\":\"9876543210\",\"line1\":\"4 Lake View\","
            + "\"city\":\"Bhopal\",\"state\":\"Madhya Pradesh\",\"pincode\":\"462001\"}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;
    @MockitoBean
    private WhatsAppSender sender;

    private String customer;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
        jdbc.update("""
                INSERT INTO products (category_id, name, slug, base_price, status)
                SELECT id, 'WA Tee', 'wa-tee', 499.00, 'ACTIVE' FROM categories WHERE code = 'TSHIRTS'""");
        jdbc.update("""
                INSERT INTO inventory (product_id, size_id, color_id, sku, quantity_on_hand, low_stock_threshold)
                SELECT p.id, s.id, c.id, ?, 10, 0 FROM products p, product_sizes s, product_colors c
                WHERE p.slug = 'wa-tee' AND s.code = 'M' AND s.size_group = 'APPAREL' AND c.code = 'BLACK'""", SKU);
        customer = register("neha@example.com");
        admin = login("admin@test.local", "TestAdmin123");
        clearInvocations(sender);
    }

    @Test
    void chatNumberIsPublicWithCountryCode() throws Exception {
        mvc.perform(get("/api/site")).andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappNumber").value("919876500000"));
    }

    @Test
    void optInAtCheckoutSendsUpdatesAndIsRemembered() throws Exception {
        mvc.perform(as(get("/api/checkout"))).andExpect(jsonPath("$.contact.whatsappOptIn").value(false));

        String number = place(true);

        List<Sent> placed = sent();
        assertThat(placed).extracting(Sent::to).containsExactlyInAnyOrder("9876543210", "919876500001");
        WhatsAppMessages.Message confirmed = placed.stream().filter(s -> s.to().equals("9876543210")).findFirst().orElseThrow().message();
        assertThat(confirmed.template()).isEqualTo("order_confirmed");
        assertThat(confirmed.params()).containsExactly("Neha", number, "Please keep ₹548 ready in cash at delivery.",
                "https://shop.test/order/" + number);
        WhatsAppMessages.Message alert = placed.stream().filter(s -> s.to().equals("919876500001")).findFirst().orElseThrow().message();
        assertThat(alert.template()).isEqualTo("new_order_alert");
        assertThat(alert.text()).contains(number, "Neha S", "₹548", "Cash on Delivery");

        // The choice becomes the account default (pre-ticks the next checkout)
        mvc.perform(as(get("/api/account/profile"))).andExpect(jsonPath("$.whatsappOptIn").value(true));
        mvc.perform(as(get("/api/orders/" + number))).andExpect(jsonPath("$.contact.whatsappUpdates").value(true));

        clearInvocations(sender);
        adminStatus(number, "{\"status\":\"PROCESSING\"}");
        adminStatus(number, "{\"status\":\"SHIPPED\",\"courierName\":\"Ekart\",\"trackingNumber\":\"EK12345\"}");
        List<Sent> shipped = sent();
        assertThat(shipped).hasSize(1);
        assertThat(shipped.get(0).message().template()).isEqualTo("order_shipped");
        assertThat(shipped.get(0).message().params()).containsExactly("Neha", number, "Ekart (tracking EK12345)",
                "https://shop.test/order/" + number);
    }

    @Test
    void withoutOptInOnlyTheShopIsAlerted() throws Exception {
        String number = place(false);

        assertThat(sent()).extracting(Sent::to).containsExactly("919876500001");
        verify(sender, never()).send(eq("9876543210"), any());
        mvc.perform(as(get("/api/orders/" + number))).andExpect(jsonPath("$.contact.whatsappUpdates").value(false));
    }

    @Test
    void customersCanSwitchUpdatesOnAndOffInTheirProfile() throws Exception {
        mvc.perform(as(put("/api/account/profile")).content("{\"fullName\":\"Neha S\",\"phone\":\"9876543210\",\"whatsappOptIn\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.whatsappOptIn").value(true));
        // Leaving the field out keeps the choice
        mvc.perform(as(put("/api/account/profile")).content("{\"fullName\":\"Neha Sharma\",\"phone\":\"9876543210\"}"))
                .andExpect(jsonPath("$.whatsappOptIn").value(true));
        mvc.perform(as(put("/api/account/profile")).content("{\"fullName\":\"Neha Sharma\",\"phone\":\"9876543210\",\"whatsappOptIn\":false}"))
                .andExpect(jsonPath("$.whatsappOptIn").value(false));
    }

    @Test
    void adminGetsOneClickLinksForTheCurrentState() throws Exception {
        String number = place(true);

        mvc.perform(get("/api/admin/orders/" + number + "/whatsapp").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("919876543210"))
                .andExpect(jsonPath("$.optedIn").value(true))
                .andExpect(jsonPath("$.automatic").value(false))
                .andExpect(jsonPath("$.messages[0].type").value("CONFIRMED"))
                .andExpect(jsonPath("$.messages[0].text").value(org.hamcrest.Matchers.startsWith("Hi Neha, thank you for shopping")))
                .andExpect(jsonPath("$.messages[0].url").value(org.hamcrest.Matchers.startsWith("https://wa.me/919876543210?text=Hi%20Neha%2C%20thank")))
                .andExpect(jsonPath("$.messages[1].type").value("HELLO"));

        mvc.perform(get("/api/admin/orders/" + number + "/whatsapp").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/orders/TS-20990101-000000/whatsapp").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    record Sent(String to, WhatsAppMessages.Message message) {
    }

    private List<Sent> sent() {
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<WhatsAppMessages.Message> message = ArgumentCaptor.forClass(WhatsAppMessages.Message.class);
        verify(sender, atLeast(0)).send(to.capture(), message.capture());
        return java.util.stream.IntStream.range(0, to.getAllValues().size())
                .mapToObj(i -> new Sent(to.getAllValues().get(i), message.getAllValues().get(i))).toList();
    }

    private String place(boolean whatsappUpdates) throws Exception {
        mvc.perform(as(post("/api/cart/items")).content("{\"sku\":\"" + SKU + "\",\"quantity\":1}")).andExpect(status().isOk());
        return field(mvc.perform(as(post("/api/checkout/place-order")).content("{\"contact\":{\"name\":\"Neha S\",\"email\":\"neha@example.com\","
                + "\"phone\":\"9876543210\"},\"address\":" + ADDRESS + ",\"paymentMethod\":\"COD\",\"expectedTotal\":548,"
                + "\"whatsappUpdates\":" + whatsappUpdates + "}")).andExpect(status().isCreated()), "orderNumber");
    }

    private void adminStatus(String number, String body) throws Exception {
        mvc.perform(patch("/api/admin/orders/" + number + "/status").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON);
    }

    private String register(String email) throws Exception {
        return field(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Neha S\",\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isCreated()), "accessToken");
    }

    private String login(String email, String password) throws Exception {
        return field(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andExpect(status().isOk()), "accessToken");
    }

    private static String field(ResultActions result, String name) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        String key = "\"" + name + "\":\"";
        int start = json.indexOf(key) + key.length();
        return json.substring(start, json.indexOf('"', start));
    }
}
