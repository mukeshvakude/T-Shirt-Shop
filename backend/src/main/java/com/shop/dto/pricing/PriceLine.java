package com.shop.dto.pricing;

import java.math.BigDecimal;

/** One extra charge in a price breakdown, per unit. */
public record PriceLine(String type, String code, String label, BigDecimal amount) {
}
