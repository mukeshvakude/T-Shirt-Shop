package com.shop.dto.pricing;

import java.math.BigDecimal;
import java.util.List;

/** Public price list for the designer (badges such as "+ INR 50") and shipping rules. */
public record PricingInfo(List<Charge> charges, BigDecimal shippingFlatCharge, BigDecimal freeShippingThreshold,
        int maxQuantityPerItem) {

    public record Charge(String type, String code, BigDecimal amount) {
    }
}
