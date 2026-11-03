package com.shop.payment;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * Razorpay Orders API over HTTPS with Basic auth (Key ID and Key Secret). The secret only ever
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
        if (config == null || !config.razorpayConfigured()) {
            throw new PaymentGatewayException("Online payment is not set up yet. Please choose Cash on Delivery.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        try {
            String response = client.post()
                    .uri("/orders")
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            JsonNode node = json.readTree(response);
            return new GatewayOrder(node.path("id").asString(), node.path("amount").asLong(), node.path("currency").asString(),
                    node.path("status").asString());
        } catch (RestClientResponseException ex) {
            log.error("Razorpay order creation failed for {}: HTTP {} {}", receipt, ex.getStatusCode().value(),
                    ex.getResponseBodyAsString());
            throw new PaymentGatewayException(ex.getStatusCode().value() == 401
                    ? "Online payment is not set up correctly. Please choose Cash on Delivery or try again later."
                    : "Online payment could not be started. Please try again.");
        } catch (RestClientException ex) {
            log.error("Razorpay unreachable while creating order for {}: {}", receipt, ex.getMessage());
            throw new PaymentGatewayException("Online payment is not reachable right now. Please try again in a moment.");
        }
    }

    private String basicAuth() {
        String raw = config.razorpayKeyId() + ":" + config.razorpayKeySecret();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
