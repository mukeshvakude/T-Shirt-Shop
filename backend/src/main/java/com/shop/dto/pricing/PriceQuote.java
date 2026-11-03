package com.shop.dto.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Server-calculated price of a custom T-Shirt. Prices include GST. {@code baseMrp} is the list
 * price of the blank T-Shirt and {@code basePrice} its current selling price (after any
 * product discount). Shipping is an estimate for this item on its own.
 */
public record PriceQuote(
        String currency,
        String productSlug,
        BigDecimal baseMrp,
        BigDecimal basePrice,
        List<PriceLine> charges,
        BigDecimal customizationPerUnit,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        BigDecimal shippingEstimate,
        BigDecimal freeShippingThreshold,
        BigDecimal amountToFreeShipping,
        boolean taxInclusive) {
}
