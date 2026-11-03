package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

import com.shop.exception.PaymentGatewayException;
import com.shop.jobs.OrderExpiryJob;
import com.shop.jobs.PaymentReconciliationJob;
import com.shop.payment.RazorpayGateway;
import com.shop.payment.RazorpayGateway.GatewayPayment;
import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;
import com.shop.util.SecureTokens;

/**
 * Razorpay payments (Phases 13 and 14) against MySQL with a stubbed gateway: starting and retrying,
 * signature plus gateway verification, capture, webhooks, reconciliation, idempotency and
 * automatic refunds.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIT {

    /** Same fake test-only secrets as application-test.yml. */
    private static final String SECRET = "integration-tests-only";
    private static final String WEBHOOK_SECRET = "webhook-integration-tests-only";
    private static final String SKU = "PAY-TEE-BLK-M";
    private static final long AMOUNT = 104700; // 2 x 499 + 49 shipping, in paise
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
    @Autowired
    private PaymentReconciliationJob reconciliationJob;
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
        doAnswer(inv -> new RazorpayGateway.GatewayRefund("rfnd_" + inv.getArgument(0), inv.getArgument(0), inv.getArgument(1), "processed"))
                .when(gateway).refund(anyString(), anyLong(), anyString(), anyMap());
    }

    // ---------------------------------------------------------------- browser verification

    @Test
    void payOnlineConfirmsTheOrderOnce() throws Exception {
        String number = placeOnlineOrder();

        start(number)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value("rzp_test_ITONLYFAKEKEY01"))
                .andExpect(jsonPath("$.razorpayOrderId").value("order_TEST1"))
                .andExpect(jsonPath("$.amount").value(AMOUNT))
                .andExpect(jsonPath("$.prefill.contact").value("9876543210"))
                .andExpect(jsonPath("$.keySecret").doesNotExist());
        start(number).andExpect(jsonPath("$.razorpayOrderId").value("order_TEST1")); // reused
        verify(gateway, times(1)).createOrder(eq(AMOUNT), eq("INR"), eq(number), anyMap());

        pay(number, "order_TEST1", "pay_TEST1", "captured")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"));
        assertThat(stock()).containsExactly(8, 0);
        assertThat(jdbc.queryForObject("select concat(status, ':', method, ':', method_detail) from payments", String.class))
                .isEqualTo("SUCCESS:upi:success@razorpay");

        pay(number, "order_TEST1", "pay_TEST1", "captured").andExpect(status().isOk()); // harmless repeat
        assertThat(stock()).containsExactly(8, 0);
        start(number).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ALREADY_PAID"));
    }

    @Test
    void authorizedPaymentsAreCaptured() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        doReturn(payment("pay_AUTH", "order_TEST1", AMOUNT, "captured")).when(gateway).capture("pay_AUTH", AMOUNT, "INR");
        pay(number, "order_TEST1", "pay_AUTH", "authorized").andExpect(jsonPath("$.status").value("CONFIRMED"));
        verify(gateway).capture("pay_AUTH", AMOUNT, "INR");
    }

    @Test
    void forgedSignaturesAndMismatchedPaymentsAreNotTrusted() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        mvc.perform(as(post("/api/payments/verify")).content("{\"orderNumber\":\"" + number + "\",\"razorpayOrderId\":\"order_TEST1\","
                        + "\"razorpayPaymentId\":\"pay_FAKE\",\"razorpaySignature\":\"" + "ab".repeat(32) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        assertThat(jdbc.queryForList("select status from payment_transactions where type = 'VERIFY'", String.class))
                .containsOnly("SIGNATURE_INVALID");
        verify(gateway, never()).fetchPayment(anyString());

        // Valid signature, but Razorpay reports a different amount: flagged, not confirmed
        doReturn(payment("pay_LOW", "order_TEST1", 100, "captured")).when(gateway).fetchPayment("pay_LOW");
        mvc.perform(as(post("/api/payments/verify")).content(verifyBody(number, "order_TEST1", "pay_LOW"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(jdbc.queryForObject("select needs_review from payments", Boolean.class)).isTrue();
        assertThat(stock()).containsExactly(10, 2);
    }

    @Test
    void gatewayOutageDuringVerificationIsRecoveredByReconciliation() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        doThrow(new PaymentGatewayException("down")).when(gateway).fetchPayment("pay_SLOW");
        mvc.perform(as(post("/api/payments/verify")).content(verifyBody(number, "order_TEST1", "pay_SLOW")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("do not pay again")));
        assertThat(jdbc.queryForObject("select order_status from orders", String.class)).isEqualTo("PENDING");

        // Five minutes later the job asks Razorpay and finds the money
        jdbc.update("UPDATE payments SET created_at = DATE_SUB(created_at, INTERVAL 10 MINUTE)");
        doReturn(List.of(payment("pay_SLOW", "order_TEST1", AMOUNT, "captured"))).when(gateway).fetchOrderPayments("order_TEST1");
        reconciliationJob.run();
        assertThat(jdbc.queryForObject("select concat(order_status, ':', payment_status) from orders", String.class))
                .isEqualTo("CONFIRMED:SUCCESS");
        assertThat(jdbc.queryForObject("select source from payment_transactions where type = 'CAPTURED'", String.class))
                .isEqualTo("RECONCILE");
    }

    // ---------------------------------------------------------------- webhooks

    @Test
    void webhookConfirmsOnceEvenWhenDeliveredTwice() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        String body = event("payment.captured", "pay_HOOK", "order_TEST1", AMOUNT, "captured");

        webhook(body, sign(body), "evt_1").andExpect(status().isOk());
        webhook(body, sign(body), "evt_1").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select concat(order_status, ':', payment_status) from orders", String.class))
                .isEqualTo("CONFIRMED:SUCCESS");
        assertThat(jdbc.queryForObject("select count(*) from payment_transactions where type = 'CAPTURED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from payment_webhook_events where event_id = 'evt_1'", String.class))
                .isEqualTo("PROCESSED");
        assertThat(stock()).containsExactly(8, 0);

        // The browser confirmation arriving afterwards changes nothing
        pay(number, "order_TEST1", "pay_HOOK", "captured").andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(stock()).containsExactly(8, 0);

        // Unsigned or wrongly signed deliveries are rejected and not stored
        webhook(body, "deadbeef", "evt_2").andExpect(status().isBadRequest());
        webhook(body, null, "evt_3").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from payment_webhook_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void failedPaymentWebhookAndUnknownEvents() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        String failed = event("payment.failed", "pay_NO", "order_TEST1", AMOUNT, "failed");
        webhook(failed, sign(failed), "evt_f").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select payment_status from orders", String.class)).isEqualTo("FAILED");

        String other = "{\"entity\":\"event\",\"event\":\"settlement.processed\",\"payload\":{}}";
        webhook(other, sign(other), "evt_s").andExpect(status().isOk());
        String foreign = event("payment.captured", "pay_X", "order_NOT_OURS", AMOUNT, "captured");
        webhook(foreign, sign(foreign), "evt_x").andExpect(status().isOk());
        assertThat(jdbc.queryForList("select concat(event_id, ':', status) from payment_webhook_events order by id", String.class))
                .containsExactly("evt_f:PROCESSED", "evt_s:IGNORED", "evt_x:IGNORED");
    }

    // ---------------------------------------------------------------- automatic refunds

    @Test
    void secondPaymentForAPaidOrderIsRefunded() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        pay(number, "order_TEST1", "pay_FIRST", "captured").andExpect(jsonPath("$.status").value("CONFIRMED"));

        String dup = event("payment.captured", "pay_SECOND", "order_TEST1", AMOUNT, "captured");
        webhook(dup, sign(dup), "evt_dup").andExpect(status().isOk());
        verify(gateway).refund(eq("pay_SECOND"), eq(AMOUNT), anyString(), anyMap());
        verify(gateway, never()).refund(eq("pay_FIRST"), anyLong(), anyString(), anyMap());
        assertThat(jdbc.queryForObject("select concat(order_status, ':', payment_status) from orders", String.class))
                .isEqualTo("CONFIRMED:SUCCESS");
    }

    @Test
    void paymentAfterTheItemSoldOutIsRefundedAndTheOrderCancelled() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        expireHolds();
        jdbc.update("UPDATE inventory SET quantity_on_hand = 1 WHERE sku = ?", SKU);

        pay(number, "order_TEST1", "pay_LATE", "captured")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        verify(gateway).refund(eq("pay_LATE"), eq(AMOUNT), anyString(), anyMap());
        assertThat(jdbc.queryForObject("select concat(status, ':', refunded_amount) from payments", String.class))
                .isEqualTo("REFUNDED:1047.00");
        assertThat(stock()).containsExactly(1, 0); // nothing half-taken
    }

    @Test
    void paymentForACancelledOrderIsRefundedAndFailedRefundsAreRetried() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        jdbc.update("UPDATE orders SET placed_at = DATE_SUB(placed_at, INTERVAL 25 HOUR)");
        expireHolds(); // releases and then cancels the unpaid order
        assertThat(jdbc.queryForObject("select order_status from orders", String.class)).isEqualTo("CANCELLED");

        // Razorpay is down when the late payment arrives: flagged, then refunded by the job
        doThrow(new PaymentGatewayException("down")).when(gateway).refund(anyString(), anyLong(), anyString(), anyMap());
        String late = event("payment.captured", "pay_LATE", "order_TEST1", AMOUNT, "captured");
        webhook(late, sign(late), "evt_late").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select concat(status, ':', needs_review) from payments", String.class)).isEqualTo("SUCCESS:1");

        doAnswer(inv -> new RazorpayGateway.GatewayRefund("rfnd_retry", inv.getArgument(0), inv.getArgument(1), "processed"))
                .when(gateway).refund(anyString(), anyLong(), anyString(), anyMap());
        reconciliationJob.run();
        assertThat(jdbc.queryForObject("select concat(status, ':', refunded_amount) from payments", String.class))
                .isEqualTo("REFUNDED:1047.00");
        assertThat(jdbc.queryForObject("select payment_status from orders", String.class)).isEqualTo("REFUNDED");
    }

    // ---------------------------------------------------------------- earlier behaviour

    @Test
    void failedAttemptCanBeRetriedWithANewRazorpayOrder() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        mvc.perform(as(post("/api/payments/failed")).content("{\"orderNumber\":\"" + number + "\",\"razorpayOrderId\":\"order_TEST1\","
                        + "\"razorpayPaymentId\":\"pay_DECLINED\",\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"Payment declined by bank\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"));
        gatewayReturns("order_TEST2");
        start(number).andExpect(jsonPath("$.razorpayOrderId").value("order_TEST2"));
        pay(number, "order_TEST2", "pay_TEST2", "captured").andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void paymentAfterTheHoldEndedWhileStockLasts() throws Exception {
        String number = placeOnlineOrder();
        start(number);
        expireHolds();
        start(number).andExpect(jsonPath("$.razorpayOrderId").value("order_TEST1"));
        assertThat(stock()).containsExactly(10, 2);
        expireHolds();
        pay(number, "order_TEST1", "pay_LATE", "captured").andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(stock()).containsExactly(8, 0);
    }

    @Test
    void guardRails() throws Exception {
        add(1);
        String cod = field(mvc.perform(as(post("/api/checkout/place-order")).content(placeBody("COD", "548"))).andExpect(status().isCreated()), "orderNumber");
        start(cod).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("NOT_ONLINE_ORDER"));

        String number = placeOnlineOrder();
        String other = register("other@example.com");
        mvc.perform(post("/api/payments/create-order").header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNumber\":\"" + number + "\"}")).andExpect(status().isNotFound());
        doThrow(new PaymentGatewayException("Online payment is not reachable right now. Please try again in a moment."))
                .when(gateway).createOrder(anyLong(), anyString(), anyString(), anyMap());
        start(number).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));
        gatewayReturns("order_TEST3");
        start(number).andExpect(status().isOk());
        mvc.perform(as(post("/api/payments/verify")).content(verifyBody(number, "order_OTHER", "pay_X")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_MISMATCH"));
    }

    // ---------------------------------------------------------------- helpers

    private static GatewayPayment payment(String id, String orderId, long amount, String status) {
        return new GatewayPayment(id, orderId, amount, "INR", status, "upi", "success@razorpay",
                "failed".equals(status) ? "BAD_REQUEST_ERROR" : null, "failed".equals(status) ? "Payment failed" : null,
                Map.of("id", id, "status", status));
    }

    private static String event(String type, String paymentId, String orderId, long amount, String status) {
        return "{\"entity\":\"event\",\"event\":\"" + type + "\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"" + paymentId
                + "\",\"entity\":\"payment\",\"amount\":" + amount + ",\"currency\":\"INR\",\"status\":\"" + status
                + "\",\"order_id\":\"" + orderId + "\",\"method\":\"upi\",\"vpa\":\"success@razorpay\"}}},\"created_at\":1793700000}";
    }

    private static String sign(String body) {
        return SecureTokens.hmacSha256Hex(WEBHOOK_SECRET, body);
    }

    private ResultActions webhook(String body, String signature, String eventId) throws Exception {
        MockHttpServletRequestBuilder b = post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Razorpay-Event-Id", eventId);
        if (signature != null) b.header("X-Razorpay-Signature", signature);
        return mvc.perform(b);
    }

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

    /** Browser confirmation with a valid signature; Razorpay reports the payment with the given status. */
    private ResultActions pay(String number, String razorpayOrderId, String paymentId, String gatewayStatus) throws Exception {
        doReturn(payment(paymentId, razorpayOrderId, AMOUNT, gatewayStatus)).when(gateway).fetchPayment(paymentId);
        return mvc.perform(as(post("/api/payments/verify")).content(verifyBody(number, razorpayOrderId, paymentId)));
    }

    private static String verifyBody(String number, String razorpayOrderId, String paymentId) {
        String signature = SecureTokens.hmacSha256Hex(SECRET, razorpayOrderId + "|" + paymentId);
        return "{\"orderNumber\":\"" + number + "\",\"razorpayOrderId\":\"" + razorpayOrderId + "\",\"razorpayPaymentId\":\""
                + paymentId + "\",\"razorpaySignature\":\"" + signature + "\"}";
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
