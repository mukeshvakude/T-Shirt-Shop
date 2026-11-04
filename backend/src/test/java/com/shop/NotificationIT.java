package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.shop.payment.RazorpayGateway;
import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;
import com.shop.util.SecureTokens;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** Email notifications (Phase 17) with a mocked mail server. */
@SpringBootTest(properties = {"spring.mail.host=smtp.test.local", "app.mail.from=orders@shop.test",
        "app.business.email=owner@shop.test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIT {

    private static final String SKU = "MAIL-TEE-BLK-M";
    private static final String ADDRESS = "{\"fullName\":\"Neha S\",\"phone\":\"9876543210\",\"line1\":\"4 Lake View\","
            + "\"city\":\"Bhopal\",\"state\":\"Madhya Pradesh\",\"pincode\":\"462001\",\"makeDefault\":false}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;
    @MockitoBean
    private JavaMailSender mail;
    @MockitoBean
    private RazorpayGateway gateway;

    private String customer;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
        when(mail.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        jdbc.update("""
                INSERT INTO products (category_id, name, slug, base_price, status)
                SELECT id, 'Mail Tee', 'mail-tee', 499.00, 'ACTIVE' FROM categories WHERE code = 'TSHIRTS'""");
        jdbc.update("""
                INSERT INTO inventory (product_id, size_id, color_id, sku, quantity_on_hand, low_stock_threshold)
                SELECT p.id, s.id, c.id, ?, 10, 0 FROM products p, product_sizes s, product_colors c
                WHERE p.slug = 'mail-tee' AND s.code = 'M' AND s.size_group = 'APPAREL' AND c.code = 'BLACK'""", SKU);
        customer = register("neha@example.com");
        admin = login("admin@test.local", "TestAdmin123");
        clearInvocations(mail);
    }

    @Test
    void orderJourneyEmails() throws Exception {
        String number = place("COD", "548");
        List<Sent> placed = sent();
        assertThat(placed).extracting(Sent::to).containsExactly("neha@example.com", "owner@shop.test");
        assertThat(placed.get(0).subject()).isEqualTo("Order " + number + " confirmed");
        assertThat(placed.get(0).body()).contains("Hi Neha", "Mail Tee", "₹548", "keep ₹548 ready", "/order/" + number);
        assertThat(placed.get(1).subject()).startsWith("New order " + number);

        clearInvocations(mail);
        adminStatus(number, "{\"status\":\"PROCESSING\"}");
        assertThat(sent()).isEmpty(); // no email for internal steps
        adminStatus(number, "{\"status\":\"SHIPPED\",\"courierName\":\"Ekart\",\"trackingNumber\":\"EK12345\"}");
        adminStatus(number, "{\"status\":\"DELIVERED\"}");
        List<Sent> journey = sent();
        assertThat(journey).extracting(Sent::subject)
                .containsExactly("Order " + number + " has shipped", "Order " + number + " delivered");
        assertThat(journey.get(0).body()).contains("Ekart", "EK12345");
    }

    @Test
    void cancellingAPaidOrderSendsOneEmailThatMentionsTheRefund() throws Exception {
        doAnswer(inv -> new RazorpayGateway.GatewayOrder("order_M", inv.getArgument(0), "INR", "created"))
                .when(gateway).createOrder(anyLong(), anyString(), anyString(), anyMap());
        doReturn(new RazorpayGateway.GatewayPayment("pay_M", "order_M", 54800, "INR", "captured", "upi", "x@upi", null, null, Map.of()))
                .when(gateway).fetchPayment("pay_M");
        doAnswer(inv -> new RazorpayGateway.GatewayRefund("rfnd_M", inv.getArgument(0), inv.getArgument(1), "processed"))
                .when(gateway).refund(anyString(), anyLong(), anyString(), anyMap());
        String number = place("RAZORPAY", "548");
        assertThat(sent()).isEmpty(); // nothing until paid
        mvc.perform(as(post("/api/payments/create-order")).content("{\"orderNumber\":\"" + number + "\"}")).andExpect(status().isOk());
        mvc.perform(as(post("/api/payments/verify")).content("{\"orderNumber\":\"" + number + "\",\"razorpayOrderId\":\"order_M\","
                + "\"razorpayPaymentId\":\"pay_M\",\"razorpaySignature\":\"" + SecureTokens.hmacSha256Hex("integration-tests-only", "order_M|pay_M") + "\"}"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(sent().get(0).body()).contains("received your payment of ₹548");

        clearInvocations(mail);
        mvc.perform(as(post("/api/orders/" + number + "/cancel"))).andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        List<Sent> cancel = sent();
        assertThat(cancel).hasSize(1);
        assertThat(cancel.get(0).subject()).isEqualTo("Order " + number + " cancelled");
        assertThat(cancel.get(0).body()).contains("being refunded", "5 to 7 working days");
    }

    @Test
    void passwordResetAndContactEmails() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"neha@example.com\"}"))
                .andExpect(status().is2xxSuccessful());
        Sent reset = sent().get(0);
        assertThat(reset.to()).isEqualTo("neha@example.com");
        assertThat(reset.body()).contains("/reset-password?token=", "expires at");

        clearInvocations(mail);
        mvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kabir\",\"email\":\"kabir@example.com\",\"subject\":\"Bulk <order>\",\"message\":\"50 tees please\"}"))
                .andExpect(status().isAccepted());
        Sent contact = sent().get(0);
        assertThat(contact.to()).isEqualTo("owner@shop.test");
        assertThat(contact.subject()).isEqualTo("Contact form: Bulk <order>");
        assertThat(contact.html()).contains("Bulk &lt;order&gt;").doesNotContain("Bulk <order>"); // escaped
    }

    @Test
    void aBrokenMailServerNeverBreaksTheOrder() throws Exception {
        doThrow(new MailSendException("connection refused")).when(mail).send(any(MimeMessage.class));
        String number = place("COD", "548");
        assertThat(jdbc.queryForObject("select order_status from orders where order_number = ?", String.class, number)).isEqualTo("CONFIRMED");
    }

    // ---------------------------------------------------------------- helpers

    record Sent(String to, String subject, String body, String html) {
    }

    private List<Sent> sent() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        try {
            verify(mail, atLeast(0)).send(captor.capture());
        } catch (AssertionError e) {
            return List.of();
        }
        List<Sent> out = new ArrayList<>();
        for (MimeMessage m : captor.getAllValues()) {
            StringBuilder text = new StringBuilder();
            StringBuilder html = new StringBuilder();
            m.saveChanges();
            collect(m, text, html);
            out.add(new Sent(m.getAllRecipients()[0].toString(), m.getSubject(), text.toString(), html.toString()));
        }
        return out;
    }

    private static void collect(Part part, StringBuilder text, StringBuilder html) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) collect(mp.getBodyPart(i), text, html);
        } else if (content instanceof String s) {
            (part.isMimeType("text/html") ? html : text).append(s);
        }
    }

    private String place(String method, String total) throws Exception {
        mvc.perform(as(post("/api/cart/items")).content("{\"sku\":\"" + SKU + "\",\"quantity\":1}")).andExpect(status().isOk());
        return field(mvc.perform(as(post("/api/checkout/place-order")).content("{\"contact\":{\"name\":\"Neha S\",\"email\":\"neha@example.com\","
                + "\"phone\":\"9876543210\"},\"address\":" + ADDRESS + ",\"saveAddress\":false,\"paymentMethod\":\"" + method
                + "\",\"expectedTotal\":" + total + "}")).andExpect(status().isCreated()), "orderNumber");
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
