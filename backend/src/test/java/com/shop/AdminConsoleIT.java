package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import com.shop.payment.RazorpayGateway;
import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;
import com.shop.util.SecureTokens;

/** Admin portal APIs (Phase 16): dashboard, orders, payments and refunds, customers, contact inbox. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminConsoleIT {

    private static final String SKU = "ADM-TEE-BLK-M";
    private static final String ADDRESS = "{\"fullName\":\"Arjun P\",\"phone\":\"9876543210\",\"line1\":\"3 Ring Road\","
            + "\"city\":\"Delhi\",\"state\":\"Delhi\",\"pincode\":\"110001\",\"makeDefault\":false}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;
    @MockitoBean
    private RazorpayGateway gateway;

    private String customer;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
        jdbc.update("""
                INSERT INTO products (category_id, name, slug, base_price, status)
                SELECT id, 'Admin Tee', 'admin-tee', 499.00, 'ACTIVE' FROM categories WHERE code = 'TSHIRTS'""");
        jdbc.update("""
                INSERT INTO inventory (product_id, size_id, color_id, sku, quantity_on_hand, low_stock_threshold)
                SELECT p.id, s.id, c.id, ?, 10, 3 FROM products p, product_sizes s, product_colors c
                WHERE p.slug = 'admin-tee' AND s.code = 'M' AND s.size_group = 'APPAREL' AND c.code = 'BLACK'""", SKU);
        customer = register("arjun@example.com");
        admin = login("admin@test.local", "TestAdmin123");
    }

    @Test
    void dashboardAndOrders() throws Exception {
        String cod = place("COD", 2, "1047");
        String unpaid = place("RAZORPAY", 1, "548");

        adminGet("/api/admin/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today.orders").value(1))       // the unpaid order is not a sale
                .andExpect(jsonPath("$.today.sales").value(1047.00))
                .andExpect(jsonPath("$.last30Days.averageOrder").value(1047.00))
                .andExpect(jsonPath("$.toShip").value(1))
                .andExpect(jsonPath("$.awaitingPayment").value(1))
                .andExpect(jsonPath("$.newCustomers30Days").value(1))
                .andExpect(jsonPath("$.salesByDay", hasSize(1)))
                .andExpect(jsonPath("$.topProducts[0].name").value("Admin Tee"))
                .andExpect(jsonPath("$.topProducts[0].quantity").value(2))
                .andExpect(jsonPath("$.recentOrders", hasSize(2)));

        adminGet("/api/admin/orders").andExpect(jsonPath("$.content", hasSize(2))).andExpect(jsonPath("$.totalElements").value(2));
        adminGet("/api/admin/orders?status=PENDING").andExpect(jsonPath("$.content[0].orderNumber").value(unpaid));
        adminGet("/api/admin/orders?q=" + cod.substring(12)).andExpect(jsonPath("$.content", hasSize(1)));
        adminGet("/api/admin/orders?q=arjun@").andExpect(jsonPath("$.content", hasSize(2)));
        adminGet("/api/admin/orders/" + cod)
                .andExpect(jsonPath("$.order.orderNumber").value(cod))
                .andExpect(jsonPath("$.nextStatuses", org.hamcrest.Matchers.contains("PROCESSING", "CANCELLED")));

        mvc.perform(as(get("/api/admin/dashboard"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void paymentsRefundAndReview() throws Exception {
        String number = place("RAZORPAY", 2, "1047");
        doAnswer(inv -> new RazorpayGateway.GatewayOrder("order_ADM", inv.getArgument(0), "INR", "created"))
                .when(gateway).createOrder(anyLong(), anyString(), anyString(), anyMap());
        doReturn(new RazorpayGateway.GatewayPayment("pay_ADM", "order_ADM", 104700, "INR", "captured", "card", "VISA 1111",
                null, null, Map.of())).when(gateway).fetchPayment("pay_ADM");
        doAnswer(inv -> new RazorpayGateway.GatewayRefund("rfnd_ADM", inv.getArgument(0), inv.getArgument(1), "processed"))
                .when(gateway).refund(anyString(), anyLong(), anyString(), anyMap());

        mvc.perform(as(post("/api/payments/create-order")).content("{\"orderNumber\":\"" + number + "\"}")).andExpect(status().isOk());
        mvc.perform(as(post("/api/payments/verify")).content("{\"orderNumber\":\"" + number + "\",\"razorpayOrderId\":\"order_ADM\","
                + "\"razorpayPaymentId\":\"pay_ADM\",\"razorpaySignature\":\""
                + SecureTokens.hmacSha256Hex("integration-tests-only", "order_ADM|pay_ADM") + "\"}")).andExpect(jsonPath("$.status").value("CONFIRMED"));

        String id = String.valueOf(jdbc.queryForObject("select id from payments", Long.class));
        adminGet("/api/admin/payments?status=SUCCESS")
                .andExpect(jsonPath("$.content[0].orderNumber").value(number))
                .andExpect(jsonPath("$.content[0].methodDetail").value("VISA 1111"));
        adminGet("/api/admin/payments/" + id).andExpect(jsonPath("$.transactions[*].type",
                org.hamcrest.Matchers.contains("ORDER_CREATED", "VERIFY", "CAPTURED")));

        // Return after delivery: the shop refunds in full; the order itself stays as it is
        adminPost("/api/admin/payments/" + id + "/refund", "{\"reason\":\"Returned: size too small\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"))
                .andExpect(jsonPath("$.payment.refundedAmount").value(1047.00));
        verify(gateway).refund(eq("pay_ADM"), eq(104700L), anyString(), anyMap());
        adminPost("/api/admin/payments/" + id + "/refund", "{\"reason\":\"again\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOT_REFUNDABLE"));
        adminGet("/api/admin/dashboard").andExpect(jsonPath("$.refunded30Days").value(1047.00));

        jdbc.update("UPDATE payments SET needs_review = TRUE");
        adminGet("/api/admin/payments?needsReview=true").andExpect(jsonPath("$.content", hasSize(1)));
        adminGet("/api/admin/orders?needsReview=true").andExpect(jsonPath("$.content[0].needsReview").value(true));
        adminPost("/api/admin/payments/" + id + "/reviewed", null).andExpect(jsonPath("$.payment.needsReview").value(false));
    }

    @Test
    void customersCanBeFoundAndDisabled() throws Exception {
        place("COD", 1, "548");
        Long id = jdbc.queryForObject("select id from users where email = 'arjun@example.com'", Long.class);
        Long adminId = jdbc.queryForObject("select id from users where email = 'admin@test.local'", Long.class);

        adminGet("/api/admin/customers")
                .andExpect(jsonPath("$.content", hasSize(1))) // admins are not listed
                .andExpect(jsonPath("$.content[0].email").value("arjun@example.com"))
                .andExpect(jsonPath("$.content[0].orders").value(1))
                .andExpect(jsonPath("$.content[0].spent").value(548.00));
        adminGet("/api/admin/customers?q=nobody").andExpect(jsonPath("$.content", hasSize(0)));
        adminGet("/api/admin/customers/" + id).andExpect(jsonPath("$.orders", hasSize(1)));
        adminGet("/api/admin/customers/" + adminId).andExpect(status().isNotFound());

        mvc.perform(patch("/api/admin/customers/" + id).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(jsonPath("$.customer.enabled").value(false));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"arjun@example.com\",\"password\":\"Secret123\"}")).andExpect(status().is4xxClientError());
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ? and revoked_at is null", Integer.class, id)).isZero();
        mvc.perform(patch("/api/admin/customers/" + adminId).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void contactInbox() throws Exception {
        mvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Riya\",\"email\":\"riya@example.com\",\"subject\":\"Bulk order\",\"message\":\"Do you print 50 team T-Shirts?\"}"))
                .andExpect(status().isAccepted());
        adminGet("/api/admin/dashboard").andExpect(jsonPath("$.newMessages").value(1));
        String id = String.valueOf(jdbc.queryForObject("select id from contact_messages", Long.class));
        adminGet("/api/admin/messages?status=NEW").andExpect(jsonPath("$.content[0].subject").value("Bulk order"));
        mvc.perform(patch("/api/admin/messages/" + id).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REPLIED\"}")).andExpect(status().isNoContent());
        adminGet("/api/admin/messages?status=NEW").andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ---------------------------------------------------------------- helpers

    private String place(String method, int quantity, String total) throws Exception {
        doAnswer(inv -> new RazorpayGateway.GatewayOrder("order_X", inv.getArgument(0), "INR", "created"))
                .when(gateway).createOrder(anyLong(), anyString(), anyString(), anyMap());
        mvc.perform(as(post("/api/cart/items")).content("{\"sku\":\"" + SKU + "\",\"quantity\":" + quantity + "}")).andExpect(status().isOk());
        return field(mvc.perform(as(post("/api/checkout/place-order")).content("{\"contact\":{\"name\":\"Arjun P\",\"email\":\"arjun@example.com\","
                + "\"phone\":\"9876543210\"},\"address\":" + ADDRESS + ",\"saveAddress\":false,\"paymentMethod\":\"" + method
                + "\",\"expectedTotal\":" + total + "}")).andExpect(status().isCreated()), "orderNumber");
    }

    private ResultActions adminGet(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + admin));
    }

    private ResultActions adminPost(String url, String body) throws Exception {
        MockHttpServletRequestBuilder b = post(url).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON);
        return mvc.perform(body == null ? b : b.content(body));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON);
    }

    private String register(String email) throws Exception {
        return field(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Arjun P\",\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
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
