package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.shop.exception.PaymentGatewayException;
import com.shop.jobs.OrderExpiryJob;
import com.shop.payment.RazorpayGateway;
import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;
import com.shop.util.SecureTokens;

/**
 * Razorpay payments (Phase 13) against MySQL with a stubbed gateway: starting and retrying,
 * signature verification, idempotency, failures, and payments that arrive after the stock hold.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIT {

    /** Same fake test-only secret as application-test.yml. */
    private static final String SECRET = "integration-tests-only";
    private static final String SKU = "PAY-TEE-BLK-M";
    private static final String ADDRESS = "{\"fullName\":\"Ravi K\",\"phone\":\"9876543210\",\"line1\":\"1 Lake Road\","
            + "\"city\":\"Kochi\",\"state\":\"Kerala\",\"pincode\":\"682001\",\"makeDefault\":false}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;
    @Autowired
    private OrderExpiryJob expiryJob;
    @MockitoBean
    private RazorpayGateway gateway;

    private String customer;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
        jdbc.update("""
                INSERT INTO products (category_id, name, slug, base_price, status)
                SELECT id, 'Pay Tee', 'pay-tee', 499.00, 'ACTIVE' FROM categories WHERE code = 'TSHIRTS'""");
        jdbc.update("""
                INSERT INTO inventory (product_id, size_id, color_id, sku, quantity_on_hand, low_stock_threshold)
                SELECT p.id, s.id, c.id, ?, 10, 0 FROM products p, product_sizes s, product_colors c
                WHERE p.slug = 'pay-tee' AND s.code = 'M' AND s.size_group = 'APPAREL' AND c.code = 'BLACK'""", SKU);
        customer = register("ravi@example.com");
        gatewayReturns("order_TEST1");
    }

    @Test
    void payOnlineConfirmsTheOrderOnce() throws Exception {
        String number = placeOnlineOrder(); // 2 x 499 + 49 shipping = 1047

        start(number)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value("rzp_test_ITONLYFAKEKEY01"))
                .andExpect(jsonPath("$.razorpayOrderId").value("order_TEST1"))
                .andExpect(jsonPath("$.amount").value(104700))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.prefill.contact").value("9876543210"))
                .andExpect(jsonPath("$.keySecret").doesNotExist());
        start(number).andExpect(jsonPath("$.razorpayOrderId").value("order_TEST1")); // reused
        verify(gateway, times(1)).createOrder(eq(104700L), eq("INR"), eq(number), anyMap());
        assertThat(jdbc.queryForObject("select payment_status from orders", String.class)).isEqualTo("PENDING");

        pay(number, "order_TEST1", "pay_TEST1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.awaitingPayment").value(false));
        assertThat(stock()).containsExactly(8, 0);
        assertThat(jdbc.queryForObject("select status from payments", String.class)).isEqualTo("SUCCESS");

        // Same confirmation again is harmless; paying again is refused
        pay(number, "order_TEST1", "pay_TEST1").andExpect(status().isOk());
        assertThat(stock()).containsExactly(8, 0);
        start(number).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ALREADY_PAID"));
    }

    @Test
    void forgedSignatureIsRejected() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        mvc.perform(as(post("/api/payments/verify")).content("{\"orderNumber\":\"" + number + "\",\"razorpayOrderId\":\"order_TEST1\","
                        + "\"razorpayPaymentId\":\"pay_FAKE\",\"razorpaySignature\":\"" + "ab".repeat(32) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        // A signature made for another payment does not work either
        mvc.perform(as(post("/api/payments/verify")).content("{\"orderNumber\":\"" + number + "\",\"razorpayOrderId\":\"order_TEST1\","
                        + "\"razorpayPaymentId\":\"pay_FAKE\",\"razorpaySignature\":\""
                        + SecureTokens.hmacSha256Hex(SECRET, "order_TEST1|pay_REAL") + "\"}"))
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        assertThat(jdbc.queryForObject("select order_status from orders", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForList("select status from payment_transactions where type = 'VERIFY'", String.class))
                .containsOnly("SIGNATURE_INVALID");
        assertThat(stock()).containsExactly(10, 2);
    }

    @Test
    void failedAttemptCanBeRetriedWithANewRazorpayOrder() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        mvc.perform(as(post("/api/payments/failed")).content("{\"orderNumber\":\"" + number + "\",\"razorpayOrderId\":\"order_TEST1\","
                        + "\"razorpayPaymentId\":\"pay_DECLINED\",\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"Payment declined by bank\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.awaitingPayment").value(true));

        gatewayReturns("order_TEST2");
        start(number).andExpect(jsonPath("$.razorpayOrderId").value("order_TEST2"));
        assertThat(jdbc.queryForList("select concat(attempt_no, ':', status) from payments order by attempt_no", String.class))
                .containsExactly("1:FAILED", "2:CREATED");
        pay(number, "order_TEST2", "pay_TEST2").andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void paymentAfterTheHoldEnded() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        expireHolds();
        assertThat(stock()).containsExactly(10, 0);

        // Retrying holds the stock again and reuses the open Razorpay order
        start(number).andExpect(jsonPath("$.razorpayOrderId").value("order_TEST1"));
        assertThat(stock()).containsExactly(10, 2);

        // Paid after the hold ended while stock is still there: taken now
        expireHolds();
        pay(number, "order_TEST1", "pay_LATE").andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(stock()).containsExactly(8, 0);
    }

    @Test
    void paymentAfterTheItemSoldOutIsFlaggedForReview() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        expireHolds();
        jdbc.update("UPDATE inventory SET quantity_on_hand = 1 WHERE sku = ?", SKU);

        pay(number, "order_TEST1", "pay_LATE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"));
        assertThat(jdbc.queryForObject("select needs_review from payments", Boolean.class)).isTrue();
        assertThat(stock()).containsExactly(1, 0); // nothing half-taken
    }

    @Test
    void guardRails() throws Exception {
        // Cash on Delivery orders are not paid online
        add(1);
        String cod = field(mvc.perform(as(post("/api/checkout/place-order")).content(placeBody("COD", "548"))).andExpect(status().isCreated()), "orderNumber");
        start(cod).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("NOT_ONLINE_ORDER"));

        String number = placeOnlineOrder();
        String other = register("other@example.com");
        mvc.perform(post("/api/payments/create-order").header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNumber\":\"" + number + "\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/create-order").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNumber\":\"" + number + "\"}")).andExpect(status().isUnauthorized());

        doThrow(new PaymentGatewayException("Online payment is not reachable right now. Please try again in a moment."))
                .when(gateway).createOrder(anyLong(), anyString(), anyString(), anyMap());
        start(number).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));

        gatewayReturns("order_TEST3");
        start(number).andExpect(status().isOk());
        pay(number, "order_OTHER", "pay_X").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_MISMATCH"));
    }

    // ---------------------------------------------------------------- helpers

    private void gatewayReturns(String razorpayOrderId) {
        doAnswer(inv -> new RazorpayGateway.GatewayOrder(razorpayOrderId, inv.getArgument(0), "INR", "created"))
                .when(gateway).createOrder(anyLong(), anyString(), anyString(), anyMap());
    }

    private String placeOnlineOrder() throws Exception {
        add(2);
        return field(mvc.perform(as(post("/api/checkout/place-order")).content(placeBody("RAZORPAY", "1047")))
                .andExpect(status().isCreated()), "orderNumber");
    }

    private void add(int quantity) throws Exception {
        mvc.perform(as(post("/api/cart/items")).content("{\"sku\":\"" + SKU + "\",\"quantity\":" + quantity + "}")).andExpect(status().isOk());
    }

    private static String placeBody(String method, String total) {
        return "{\"contact\":{\"name\":\"Ravi K\",\"email\":\"ravi@example.com\",\"phone\":\"9876543210\"},\"address\":" + ADDRESS
                + ",\"saveAddress\":false,\"paymentMethod\":\"" + method + "\",\"expectedTotal\":" + total + "}";
    }

    private ResultActions start(String number) throws Exception {
        return mvc.perform(as(post("/api/payments/create-order")).content("{\"orderNumber\":\"" + number + "\"}"));
    }

    private ResultActions pay(String number, String razorpayOrderId, String paymentId) throws Exception {
        String signature = SecureTokens.hmacSha256Hex(SECRET, razorpayOrderId + "|" + paymentId);
        return mvc.perform(as(post("/api/payments/verify")).content("{\"orderNumber\":\"" + number + "\",\"razorpayOrderId\":\""
                + razorpayOrderId + "\",\"razorpayPaymentId\":\"" + paymentId + "\",\"razorpaySignature\":\"" + signature + "\"}"));
    }

    private void expireHolds() {
        jdbc.update("UPDATE inventory_reservations SET expires_at = DATE_SUB(expires_at, INTERVAL 1 DAY) WHERE status = 'ACTIVE'");
        expiryJob.run();
    }

    private Integer[] stock() {
        return jdbc.queryForObject("select quantity_on_hand, quantity_reserved from inventory where sku = ?",
                (rs, n) -> new Integer[] {rs.getInt(1), rs.getInt(2)}, SKU);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON);
    }

    private String register(String email) throws Exception {
        return field(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Ravi K\",\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isCreated()), "accessToken");
    }

    private static String field(ResultActions result, String name) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        String key = "\"" + name + "\":\"";
        int start = json.indexOf(key) + key.length();
        return json.substring(start, json.indexOf('"', start));
    }
}
