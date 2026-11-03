package com.shop.dto.home;

import java.math.BigDecimal;
import java.time.Instant;

/** An advertised coupon. {@code headline} is a ready-to-show summary such as "10% off". */
public record OfferResponse(String code, String headline, String description, String discountType,
        BigDecimal discountValue, BigDecimal minOrderAmount, BigDecimal maxDiscount, Instant validTo) {
}
