package com.shop.payment;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.shop.config.AppProperties;
import com.shop.exception.PaymentGatewayException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Razorpay REST API over HTTPS with Basic auth (Key ID and Key Secret). The secret only ever
 * leaves this server in the Authorization header to Razorpay and is never logged.
 */
@Component
public class RazorpayHttpGateway implements RazorpayGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayHttpGateway.class);

    private final AppProperties.Payments config;
    private final JsonMapper json;
    private final RestClient client;

    public RazorpayHttpGateway(AppProperties properties, JsonMapper json) {
        this.config = properties.payments();
        this.json = json;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(config == null ? "https://api.razorpay.com/v1" : config.apiBase())
                .build();
    }

    @Override
    public GatewayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        JsonNode node = call("create order for " + receipt, () -> post("/orders", body));
        return new GatewayOrder(node.path("id").asString(), node.path("amount").asLong(), node.path("currency").asString(),
                node.path("status").asString());
    }

    @Override
    public GatewayPayment fetchPayment(String paymentId) {
        return payment(call("fetch payment " + paymentId, () -> get("/payments/" + paymentId)));
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(String razorpayOrderId) {
        JsonNode node = call("fetch payments of " + razorpayOrderId, () -> get("/orders/" + razorpayOrderId + "/payments"));
        List<GatewayPayment> out = new ArrayList<>();
        node.path("items").forEach(p -> out.add(payment(p)));
        return out;
    }

    @Override
    public GatewayPayment capture(String paymentId, long amountPaise, String currency) {
        Map<String, Object> body = Map.of("amount", amountPaise, "currency", currency);
        return payment(call("capture " + paymentId, () -> post("/payments/" + paymentId + "/capture", body)));
    }

    @Override
    public GatewayRefund refund(String paymentId, long amountPaise, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("speed", "normal");
        body.put("receipt", receipt);
        body.put("notes", notes);
        JsonNode node = call("refund " + paymentId, () -> post("/payments/" + paymentId + "/refund", body));
        return new GatewayRefund(node.path("id").asString(), node.path("payment_id").asString(), node.path("amount").asLong(),
                node.path("status").asString());
    }

    /** Converts a Razorpay payment entity, keeping only fields useful for support (no card numbers). */
    public static GatewayPayment payment(JsonNode p) {
        String method = text(p, "method");
        String detail = switch (method == null ? "" : method) {
            case "upi" -> text(p, "vpa");
            case "netbanking" -> text(p, "bank");
            case "wallet" -> text(p, "wallet");
            case "card" -> p.path("card").isObject()
                    ? (text(p.path("card"), "network") + " " + text(p.path("card"), "last4")).replace("null", "").trim()
                    : text(p, "card_id");
            default -> null;
        };
        Map<String, Object> raw = new LinkedHashMap<>();
        for (String f : List.of("id", "order_id", "amount", "currency", "status", "method", "captured", "error_code",
                "error_description", "error_reason", "amount_refunded", "created_at")) {
            JsonNode v = p.get(f);
            if (v != null && !v.isNull()) raw.put(f, v.isNumber() ? v.numberValue() : v.isBoolean() ? v.booleanValue() : v.asString());
        }
        if (detail != null) raw.put("method_detail", detail);
        return new GatewayPayment(text(p, "id"), text(p, "order_id"), p.path("amount").asLong(), text(p, "currency"),
                text(p, "status"), method, detail, text(p, "error_code"), text(p, "error_description"), raw);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    private String get(String uri) {
        return client.get().uri(uri).header(HttpHeaders.AUTHORIZATION, basicAuth()).retrieve().body(String.class);
    }

    private String post(String uri, Object body) {
        return client.post().uri(uri).header(HttpHeaders.AUTHORIZATION, basicAuth()).contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(body)).retrieve().body(String.class);
    }

    private JsonNode call(String what, Supplier<String> request) {
        if (config == null || !config.razorpayConfigured()) {
            throw new PaymentGatewayException("Online payment is not set up yet. Please choose Cash on Delivery.");
        }
        try {
            return json.readTree(request.get());
        } catch (RestClientResponseException ex) {
            log.error("Razorpay {} failed: HTTP {} {}", what, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new PaymentGatewayException(ex.getStatusCode().value() == 401
                    ? "Online payment is not set up correctly. Please choose Cash on Delivery or try again later."
                    : "The payment service could not complete the request. Please try again.");
        } catch (RestClientException ex) {
            log.error("Razorpay unreachable ({}): {}", what, ex.getMessage());
            throw new PaymentGatewayException("Online payment is not reachable right now. Please try again in a moment.");
        }
    }

    private String basicAuth() {
        String raw = config.razorpayKeyId() + ":" + config.razorpayKeySecret();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
