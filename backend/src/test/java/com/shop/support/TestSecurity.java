package com.shop.support;

import java.time.Duration;
import java.util.Base64;

import com.shop.config.AppProperties;
import com.shop.config.SecurityProperties;

/** Ready-made settings for unit tests. */
public final class TestSecurity {

    public static final String SECRET = Base64.getEncoder()
            .encodeToString("unit-test-secret-key-0123456789-abcdefghij".getBytes());

    private TestSecurity() {
    }

    public static SecurityProperties properties() {
        return new SecurityProperties(
                new SecurityProperties.Jwt(SECRET, "tshirt-shop-test", Duration.ofMinutes(15), Duration.ofDays(7)),
                new SecurityProperties.RefreshCookie("tshirt_rt", "/api/auth", false, "Strict"),
                Duration.ofMinutes(30),
                new SecurityProperties.Login(5, Duration.ofMinutes(15)),
                new SecurityProperties.RateLimit(1000, 1000, 1000),
                new SecurityProperties.Admin(null, null, null),
                false);
    }

    public static AppProperties app() {
        return new AppProperties("http://localhost:4200/",
                new AppProperties.Cors(java.util.List.of("http://localhost:4200")),
                new AppProperties.Storage("local", "./uploads"),
                new AppProperties.Stock(15),
                true);
    }
}
