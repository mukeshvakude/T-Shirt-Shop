package com.shop.dto.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.shop.dto.auth.UserResponse;

/** Everything the account dashboard shows, in one request. */
public record DashboardResponse(
        UserResponse profile,
        OrderStats orders,
        List<RecentOrder> recentOrders,
        long wishlistCount,
        long addressCount,
        AddressResponse defaultAddress) {

    /** @param active orders not yet delivered or cancelled */
    public record OrderStats(long total, long active, long delivered, long cancelled) {
    }

    public record RecentOrder(String orderNumber, Instant placedAt, String orderStatus, String paymentStatus,
            BigDecimal grandTotal, long itemCount) {
    }
}
