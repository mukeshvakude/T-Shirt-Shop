package com.shop.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProductionReadinessCheckTest {

    private static AppProperties app(String url, List<String> origins, String keyId, String webhookSecret, String dir, boolean docs) {
        return new AppProperties(url, new AppProperties.Cors(origins),
                new AppProperties.Storage("local", dir, 5_242_880, 4000), new AppProperties.Stock(15),
                new AppProperties.Payments(keyId, keyId == null ? null : "secret", null, webhookSecret),
                new AppProperties.Business("Shop", "Shop Pvt Ltd", "Pune", null, null, null), docs);
    }

    private static SecurityProperties security(boolean secureCookie, boolean logResetLinks) {
        return new SecurityProperties(
                new SecurityProperties.Jwt("secret", "tshirt-shop", Duration.ofMinutes(15), Duration.ofDays(7)),
                new SecurityProperties.RefreshCookie("tshirt_rt", "/api/auth", secureCookie, "Strict"),
                Duration.ofMinutes(30), new SecurityProperties.Login(5, Duration.ofMinutes(15)),
                new SecurityProperties.RateLimit(10, 10, 5, 10, 5),
                new SecurityProperties.Admin(null, null, null), logResetLinks);
    }

    @Test
    void liveSettingsPassWithoutWarnings() {
        var report = ProductionReadinessCheck.check(
                app("https://shop.example.in", List.of("https://shop.example.in"), "rzp_live_Abc12345XYZ", "whsec_0123456789abcdef", "/var/lib/tshirt-shop/uploads", false),
                security(true, false), true);

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).isEmpty();
    }

    @Test
    void unsafeSettingsStopTheStart() {
        var report = ProductionReadinessCheck.check(
                app("http://shop.example.in", List.of("http://localhost:4200"), null, null, "/srv/uploads", true),
                security(false, true), true);

        assertThat(report.errors()).hasSize(6)
                .anyMatch(e -> e.startsWith("APP_FRONTEND_URL"))
                .anyMatch(e -> e.startsWith("APP_CORS_ALLOWED_ORIGINS"))
                .anyMatch(e -> e.startsWith("REFRESH_COOKIE_SECURE"))
                .anyMatch(e -> e.startsWith("Password reset links"))
                .anyMatch(e -> e.startsWith("API docs"))
                .anyMatch(e -> e.startsWith("RAZORPAY_KEY_ID"));
    }

    @Test
    void missingOptionalSettingsOnlyWarn() {
        var report = ProductionReadinessCheck.check(
                app("https://shop.example.in", List.of("https://shop.example.in"), "rzp_test_Abc12345XYZ", "", "./uploads", false),
                security(true, false), false);

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).hasSize(4)
                .anyMatch(w -> w.contains("TEST mode"))
                .anyMatch(w -> w.startsWith("RAZORPAY_WEBHOOK_SECRET"))
                .anyMatch(w -> w.startsWith("Email"))
                .anyMatch(w -> w.startsWith("STORAGE_LOCAL_DIR"));
    }
}
