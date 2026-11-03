package com.shop.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Typed application settings bound from the {@code app.*} properties.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String frontendUrl,
        @NotNull @Valid Cors cors,
        @NotNull @Valid Storage storage,
        @NotNull @Valid Stock stock,
        @Valid Payments payments,
        @Valid Business business,
        boolean apiDocsEnabled) {

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    public record Storage(@NotBlank String provider, @NotBlank String localDir, @Min(1024) long maxImageBytes,
            @Min(100) int maxImageDimension) {
    }

    public record Stock(@Min(1) int reservationMinutes) {
    }

    /**
     * Seller details printed on invoices. Empty values are left out; with a GSTIN the document is a
     * "Tax invoice", otherwise an "Invoice".
     */
    public record Business(String name, String legalName, String address, String gstin, String email, String phone) {

        public String displayName() {
            return name == null || name.isBlank() ? "T-Shirt Shop" : name;
        }

        public boolean hasGstin() {
            return gstin != null && !gstin.isBlank();
        }
    }

    /**
     * Razorpay credentials from the environment. The secret stays on the server; online payment is
     * offered only when real keys are configured (not the .env.example placeholders).
     */
    public record Payments(String razorpayKeyId, String razorpayKeySecret, String razorpayApiBase, String razorpayWebhookSecret) {

        /** Webhooks are accepted only with a real secret (not the .env.example placeholder). */
        public boolean webhookConfigured() {
            return razorpayWebhookSecret != null && razorpayWebhookSecret.length() >= 16 && !"change-me".equals(razorpayWebhookSecret);
        }

        public String apiBase() {
            return razorpayApiBase == null || razorpayApiBase.isBlank() ? "https://api.razorpay.com/v1" : razorpayApiBase;
        }

        public boolean razorpayConfigured() {
            return razorpayKeyId != null && razorpayKeyId.matches("^rzp_(test|live)_[A-Za-z0-9]{8,}$")
                    && !razorpayKeyId.contains("xxxx") && razorpayKeySecret != null && !razorpayKeySecret.isBlank()
                    && !"change-me".equals(razorpayKeySecret);
        }
    }
}
