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
        boolean apiDocsEnabled) {

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    public record Storage(@NotBlank String provider, @NotBlank String localDir, @Min(1024) long maxImageBytes,
            @Min(100) int maxImageDimension) {
    }

    public record Stock(@Min(1) int reservationMinutes) {
    }
}
