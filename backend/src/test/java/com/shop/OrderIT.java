package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.shop.payment.RazorpayGateway;
import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;
import com.shop.util.SecureTokens;

/**
 * Order management (Phase 15) against MySQL: history, details, customer cancellation with stock
 * return and refunds, admin status changes with tracking, cash collection, and PDF invoices.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderIT {

    private static final String SKU = "ORD-TEE-BLK-M";
    private static final String ADDRESS = "{\"fullName\":\"Isha N\",\"phone\":\"9876543210\",\"line1\":\"7 Hill Road\","
            + "\"city\":\"Mumbai\",\"state\":\"Maharashtra\",\"pincode\":\"400050\",\"makeDefault\":false}";

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
                SELECT id, 'Order Tee', 'order-tee', 499.00, 'ACTIVE' FROM categories WHERE code = 'TSHIRTS'""");
        jdbc.update("""
                INSERT INTO inventory (product_id, size_id, color_id, sku, quantity_on_hand, low_stock_threshold)
                SELECT p.id, s.id, c.id, ?, 10, 0 FROM products p, product_sizes s, product_colors c
                WHERE p.slug = 'order-tee' AND s.code = 'M' AND s.size_group = 'APPAREL' AND c.code = 'BLACK'""", SKU);
        jdbc.update("""
                INSERT INTO coupons (code, description, discount_type, discount_value, min_order_amount, valid_from, valid_to, per_user_limit)
                VALUES ('SAVE50', 'INR 50 off', 'FLAT', 50, 0, '2020-01-01', '2099-01-01', 1)""");
        customer = register("isha@example.com");
        admin = login("admin@test.local", "TestAdmin123");
    }

    @Test
    void historyAndDetails() throws Exception {
        String first = placeCod(1, "548");
        String second = placeCod(2, "1047");

        mvc.perform(as(get("/api/orders")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].orderNumber").value(second))
                .andExpect(jsonPath("$.content[0].itemCount").value(2))
                .andExpect(jsonPath("$.content[0].firstItemName").value("Order Tee"))
                .andExpect(jsonPath("$.content[1].orderNumber").value(first))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(as(get("/api/orders/" + first)))
                .andExpect(jsonPath("$.canCancel").value(true))
                .andExpect(jsonPath("$.invoiceAvailable").value(true))
                .andExpect(jsonPath("$.confirmedAt", notNullValue()));
        String other = register("other@example.com");
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + other)).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void customerCancelsACashOnDeliveryOrder() throws Exception {
        add(2);
        mvc.perform(as(put("/api/cart/coupon")).content("{\"code\":\"SAVE50\"}")).andExpect(status().isOk());
        String number = field(mvc.perform(as(post("/api/checkout/place-order")).content(placeBody("COD", "997"))).andExpect(status().isCreated()), "orderNumber");
        assertThat(stock()).isEqualTo(8);
        assertThat(jdbc.queryForObject("select used_count from coupons", Integer.class)).isEqualTo(1);

        mvc.perform(as(post("/api/orders/" + number + "/cancel")).content("{\"reason\":\"Ordered the wrong size\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Cancelled by you: Ordered the wrong size"))
                .andExpect(jsonPath("$.canCancel").value(false))
                .andExpect(jsonPath("$.invoiceAvailable").value(false));
        assertThat(stock()).isEqualTo(10); // back on the shelf
        assertThat(jdbc.queryForObject("select used_count from coupons", Integer.class)).isZero();
        mvc.perform(as(post("/api/orders/" + number + "/cancel"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    void cancellingAPaidOnlineOrderRefundsIt() throws Exception {
        doAnswer(inv -> new RazorpayGateway.GatewayOrder("order_ORD1", inv.getArgument(0), "INR", "created"))
                .when(gateway).createOrder(anyLong(), anyString(), anyString(), anyMap());
        doReturn(new RazorpayGateway.GatewayPayment("pay_ORD1", "order_ORD1", 104700, "INR", "captured", "upi", "success@razorpay",
                null, null, Map.of())).when(gateway).fetchPayment("pay_ORD1");
        doAnswer(inv -> new RazorpayGateway.GatewayRefund("rfnd_ORD1", inv.getArgument(0), inv.getArgument(1), "processed"))
                .when(gateway).refund(anyString(), anyLong(), anyString(), anyMap());

        add(2);
        String number = field(mvc.perform(as(post("/api/checkout/place-order")).content(placeBody("RAZORPAY", "1047"))).andExpect(status().isCreated()), "orderNumber");
        mvc.perform(as(post("/api/payments/create-order")).content("{\"orderNumber\":\"" + number + "\"}")).andExpect(status().isOk());
        mvc.perform(as(post("/api/payments/verify")).content("{\"orderNumber\":\"" + number + "\",\"razorpayOrderId\":\"order_ORD1\","
                + "\"razorpayPaymentId\":\"pay_ORD1\",\"razorpaySignature\":\""
                + SecureTokens.hmacSha256Hex("integration-tests-only", "order_ORD1|pay_ORD1") + "\"}"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(stock()).isEqualTo(8);

        mvc.perform(as(post("/api/orders/" + number + "/cancel")))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        verify(gateway).refund(eq("pay_ORD1"), eq(104700L), anyString(), anyMap());
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void adminMovesTheOrderAlongAndCashIsCollectedOnDelivery() throws Exception {
        String number = placeCod(1, "548");
        mvc.perform(as(patch("/api/admin/orders/" + number + "/status")).content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isForbidden());

        adminStatus(number, "{\"status\":\"PROCESSING\"}").andExpect(jsonPath("$.status").value("PROCESSING"));
        mvc.perform(as(post("/api/orders/" + number + "/cancel"))).andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        adminStatus(number, "{\"status\":\"SHIPPED\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TRACKING_REQUIRED"));
        adminStatus(number, "{\"status\":\"DELIVERED\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_CHANGE"));
        adminStatus(number, "{\"status\":\"SHIPPED\",\"courierName\":\"Delhivery\",\"trackingNumber\":\"DLV123456\"}")
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.shippedAt", notNullValue()));
        adminStatus(number, "{\"status\":\"OUT_FOR_DELIVERY\"}").andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));
        adminStatus(number, "{\"status\":\"DELIVERED\",\"note\":\"Handed to the customer\"}")
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"));
        adminStatus(number, "{\"status\":\"CANCELLED\"}").andExpect(status().isUnprocessableContent());

        mvc.perform(as(get("/api/orders/" + number)))
                .andExpect(jsonPath("$.courierName").value("Delhivery"))
                .andExpect(jsonPath("$.trackingNumber").value("DLV123456"))
                .andExpect(jsonPath("$.deliveredAt", notNullValue()))
                .andExpect(jsonPath("$.history[*].status", org.hamcrest.Matchers.contains(
                        "CONFIRMED", "PROCESSING", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED")))
                .andExpect(jsonPath("$.history[2].note").value("Shipped with Delhivery (tracking DLV123456)"))
                .andExpect(jsonPath("$.history[4].note").value("Handed to the customer"));
        assertThat(jdbc.queryForObject("select concat(provider, ':', status, ':', amount) from payments", String.class))
                .isEqualTo("COD:SUCCESS:548.00");
        assertThat(jdbc.queryForObject("select type from payment_transactions", String.class)).isEqualTo("COD_COLLECTED");
        assertThat(stock()).isEqualTo(9); // delivered goods stay sold
    }

    @Test
    void invoicePdf() throws Exception {
        String number = placeCod(2, "1047");
        byte[] pdf = mvc.perform(as(get("/api/orders/" + number + "/invoice")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("INV-" + number.substring(3) + ".pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("INVOICE", "INV-" + number.substring(3), "Order Tee", "INR 1,047.00", "Cash on Delivery", "Isha N");
        assertThat(text).doesNotContain("TAX INVOICE"); // no GSTIN configured

        String other = register("other@example.com");
        mvc.perform(get("/api/orders/" + number + "/invoice").header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/orders/" + number + "/invoice").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        // No invoice before the order is confirmed
        doAnswer(inv -> new RazorpayGateway.GatewayOrder("order_X", inv.getArgument(0), "INR", "created"))
                .when(gateway).createOrder(anyLong(), anyString(), anyString(), anyMap());
        add(1);
        String pending = field(mvc.perform(as(post("/api/checkout/place-order")).content(placeBody("RAZORPAY", "548"))).andExpect(status().isCreated()), "orderNumber");
        mvc.perform(as(get("/api/orders/" + pending + "/invoice"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_AVAILABLE"));
    }

    // ---------------------------------------------------------------- helpers

    private String placeCod(int quantity, String total) throws Exception {
        add(quantity);
        return field(mvc.perform(as(post("/api/checkout/place-order")).content(placeBody("COD", total))).andExpect(status().isCreated()), "orderNumber");
    }

    private ResultActions adminStatus(String number, String body) throws Exception {
        return mvc.perform(patch("/api/admin/orders/" + number + "/status").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void add(int quantity) throws Exception {
        mvc.perform(as(post("/api/cart/items")).content("{\"sku\":\"" + SKU + "\",\"quantity\":" + quantity + "}")).andExpect(status().isOk());
    }

    private static String placeBody(String method, String total) {
        return "{\"contact\":{\"name\":\"Isha N\",\"email\":\"isha@example.com\",\"phone\":\"9876543210\"},\"address\":" + ADDRESS
                + ",\"saveAddress\":false,\"paymentMethod\":\"" + method + "\",\"expectedTotal\":" + total + "}";
    }

    private int stock() {
        return jdbc.queryForObject("select quantity_on_hand - quantity_reserved from inventory where sku = ?", Integer.class, SKU);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON);
    }

    private String register(String email) throws Exception {
        return field(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Isha N\",\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
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
