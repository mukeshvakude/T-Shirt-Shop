package com.shop.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One row in the customer's order history. */
public record OrderSummary(
        String orderNumber,
        Instant placedAt,
        String status,
        String paymentStatus,
        String paymentMethod,
        BigDecimal total,
        int itemCount,
        String firstItemName,
        List<String> images,
        boolean awaitingPayment) {
}
