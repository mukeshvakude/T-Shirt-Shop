package com.shop.dto.cart;

import java.math.BigDecimal;
import java.util.List;

import com.shop.dto.pricing.PriceLine;

/**
 * The cart priced on the server at the moment of the request. Lines that cannot be bought now
 * (out of stock, not enough stock, no longer sold) are listed with a status and message but are
 * left out of the totals, and {@code canCheckout} is false until they are fixed.
 */
public record CartResponse(
        String currency,
        List<Line> items,
        int itemCount,
        BigDecimal subtotal,
        Coupon coupon,
        BigDecimal discount,
        BigDecimal shipping,
        BigDecimal total,
        BigDecimal freeShippingThreshold,
        BigDecimal amountToFreeShipping,
        boolean taxInclusive,
        boolean canCheckout,
        List<String> notices) {

    /** Line status: OK, LOW_STOCK (still OK), NOT_ENOUGH_STOCK, OUT_OF_STOCK, UNAVAILABLE. */
    public record Line(
            Long id,
            String key,
            String type,
            Long productId,
            String productSlug,
            String name,
            String imageUrl,
            String sku,
            String sizeCode,
            String sizeLabel,
            String colorCode,
            String colorName,
            String colorHex,
            int quantity,
            int maxQuantity,
            BigDecimal unitPrice,
            BigDecimal unitMrp,
            BigDecimal lineTotal,
            String status,
            String message,
            List<Variant> variants,
            Design design) {
    }

    public record Variant(String sku, String sizeCode, String sizeLabel, String colorCode, String colorName, String colorHex,
            boolean inStock) {
    }

    public record Design(String code, String name, String previewFrontUrl, String previewBackUrl, List<String> placements,
            boolean premiumPrint, List<String> texts, BigDecimal basePrice, List<PriceLine> charges) {
    }

    public record Coupon(String code, String description, boolean applied, BigDecimal discount, String message) {
    }
}
