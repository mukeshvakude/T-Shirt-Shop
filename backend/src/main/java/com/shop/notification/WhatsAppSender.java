package com.shop.notification;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.shop.config.WhatsAppProperties;

/**
 * Sends approved template messages through the Meta WhatsApp Cloud API when an access token and
 * phone number id are configured; otherwise logs that a message would have been sent. The token
 * stays on the server and is never logged; numbers are masked in logs. Failures are logged and
 * never break the action that triggered the message.
 */
@Component
public class WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppSender.class);

    private final WhatsAppProperties properties;
    private final RestClient http;

    public WhatsAppSender(WhatsAppProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        return properties.cloudConfigured();
    }

    /** @return true when Meta accepted the message */
    public boolean send(String phone, WhatsAppMessages.Message message) {
        String to = WhatsAppProperties.digits(phone);
        if (to == null || message.template() == null) {
            return false;
        }
        if (!configured()) {
            log.info("WhatsApp not sent (Cloud API is not configured): {} to {}", message.template(), mask(to));
            return false;
        }
        WhatsAppProperties.CloudApi api = properties.cloudApi();
        List<Map<String, String>> parameters = message.params().stream().map(p -> Map.of("type", "text", "text", p)).toList();
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "template",
                "template", Map.of(
                        "name", message.template(),
                        "language", Map.of("code", api.language()),
                        "components", List.of(Map.of("type", "body", "parameters", parameters))));
        try {
            http.post()
                    .uri(api.base() + "/{id}/messages", api.phoneNumberId())
                    .header("Authorization", "Bearer " + api.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("WhatsApp sent: {} to {}", message.template(), mask(to));
            return true;
        } catch (Exception ex) {
            log.error("WhatsApp {} to {} could not be sent: {}", message.template(), mask(to), ex.getMessage());
            return false;
        }
    }

    /** 91******3210 */
    static String mask(String digits) {
        return digits.length() <= 6 ? "***" : digits.substring(0, 2) + "*".repeat(digits.length() - 6) + digits.substring(digits.length() - 4);
    }
}
