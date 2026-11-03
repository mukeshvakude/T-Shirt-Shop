package com.shop.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.shop.dto.pricing.PriceLine;

/** An order as the customer sees it: snapshot prices, address, status and history. */
public record OrderResponse(
        String orderNumber,
        String status,
        String paymentStatus,
        String paymentMethod,
        Instant placedAt,
        Instant reservationExpiresAt,
        boolean awaitingPayment,
        List<Item> items,
        BigDecimal itemsTotal,
        BigDecimal customizationTotal,
        BigDecimal shipping,
        BigDecimal discount,
        String couponCode,
        BigDecimal total,
        String currency,
        Contact contact,
        Address shippingAddress,
        String note,
        String cancelReason,
        List<HistoryEntry> history) {

    public record Item(String name, String sku, String sizeCode, String colorCode, String colorName, String imageUrl,
            int quantity, BigDecimal unitBasePrice, BigDecimal unitCustomizationCharge, BigDecimal lineTotal,
            String designCode, String designName, List<PriceLine> charges) {
    }

    public record Contact(String name, String email, String phone) {
    }

    public record Address(String fullName, String phone, String line1, String line2, String landmark, String city,
            String state, String pincode) {
    }

    public record HistoryEntry(String status, Instant at, String note) {
    }
}
