package com.shop.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shop.entity.CustomDesign;
import com.shop.entity.InventoryReservation;
import com.shop.entity.Order;
import com.shop.entity.OrderItem;
import com.shop.entity.OrderStatusHistory;
import com.shop.repository.CouponRedemptionRepository;
import com.shop.repository.CouponRepository;
import com.shop.repository.OrderRepository;
import com.shop.repository.OrderStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Shared order state changes: cancelling gives back held stock, the coupon use and design locks,
 * and every change is written to the order history. The caller holds the order row lock.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderLifecycle {

    private final StockService stock;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;

    /** Cancels the order. Payment status is left to the caller (cancelled, failed or refunded). */
    public void cancel(Order order, String reason, Long changedBy, Instant now) {
        if (order.getOrderStatus() == Order.Status.CANCELLED) {
            return;
        }
        // Held stock goes back, and stock the order had already taken returns to the shelf (not shipped yet).
        stock.release(order.getId(), InventoryReservation.Status.RELEASED, now);
        stock.restock(order.getId(), now);
        String from = order.getOrderStatus().name();
        order.setOrderStatus(Order.Status.CANCELLED);
        order.setCancelledAt(now);
        order.setCancelReason(reason);
        order.setReservationExpiresAt(null);
        redemptions.findByOrderId(order.getId()).ifPresent(redemption -> {
            coupons.findByIdForUpdate(redemption.getCouponId())
                    .ifPresent(c -> c.setUsedCount(Math.max(0, c.getUsedCount() - 1)));
            redemptions.delete(redemption);
        });
        for (OrderItem item : order.getItems()) {
            CustomDesign design = item.getCustomDesign();
            if (design != null && orders.countOtherOpenOrdersWithDesign(design.getId(), order.getId()) == 0) {
                design.setLocked(false);
                design.setStatus(CustomDesign.Status.DRAFT);
            }
        }
        record(order, from, reason, changedBy, now);
    }

    /** Writes a history entry; {@code from} equal to the current status records a note without a status change. */
    public void record(Order order, String from, String note, Long changedBy, Instant now) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(order.getId());
        h.setFromStatus(from);
        h.setToStatus(order.getOrderStatus().name());
        h.setChangedBy(changedBy);
        h.setNote(note == null ? null : note.length() > 300 ? note.substring(0, 300) : note);
        h.setCreatedAt(now);
        history.save(h);
    }
}
