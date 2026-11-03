package com.shop.dto.pricing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Every chargeable choice, whether a charge row exists or not ({@code id} is null and the amount
 * zero when no charge is set).
 */
public record AdminChargeResponse(Long id, String type, String code, String label, BigDecimal amount, boolean active,
        String description, Instant updatedAt) {
}
