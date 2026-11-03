package com.shop.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.entity.InventoryReservation;
import com.shop.entity.Order;
import com.shop.entity.OrderItem;
import com.shop.entity.OrderStatusHistory;
import com.shop.repository.CouponRedemptionRepository;
import com.shop.repository.CouponRepository;
import com.shop.repository.InventoryReservationRepository;
import com.shop.repository.OrderRepository;
import com.shop.repository.OrderStatusHistoryRepository;
import com.shop.service.AppSettingService;

import lombok.RequiredArgsConstructor;

/**
 * Abandoned online payments (architecture section 7): stock held for an unpaid order is released
 * when its payment window ends, and the order is cancelled when it is still unpaid after
 * {@code order.unpaid_cancel_hours}. Each order is handled in its own transaction under a row lock.
 */
@Service
@RequiredArgsConstructor
public class OrderExpiryService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryService.class);

    private final OrderRepository orders;
    private final InventoryReservationRepository reservations;
    private final OrderStatusHistoryRepository history;
    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final com.shop.repository.PaymentRepository payments;
    private final StockService stock;
    private final AppSettingService settings;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> ordersWithExpiredHolds() {
        return reservations.findOrderIdsWithExpiredHolds(clock.instant());
    }

    @Transactional(readOnly = true)
    public List<Long> unpaidOrdersToCancel() {
        Duration window = Duration.ofHours(settings.getInt("order.unpaid_cancel_hours"));
        return orders.findUnpaidPlacedBefore(clock.instant().minus(window));
    }

    /** Releases the stock held for an unpaid order whose payment window has ended. */
    @Transactional
    public void releaseExpiredHolds(Long orderId) {
        Instant now = clock.instant();
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getPaymentStatus() == Order.PaymentStatus.SUCCESS) {
            return;
        }
        int released = stock.release(orderId, InventoryReservation.Status.EXPIRED, now);
        if (released == 0) {
            return;
        }
        if (order.getPaymentStatus() == Order.PaymentStatus.CREATED || order.getPaymentStatus() == Order.PaymentStatus.PENDING) {
            order.setPaymentStatus(Order.PaymentStatus.CANCELLED);
        }
        record(order, order.getOrderStatus().name(), now,
                "Payment window ended; held items were released. The order can still be paid if stock allows.");
        log.info("Order {}: payment window ended, released {} stock hold(s)", order.getOrderNumber(), released);
    }

    /** Cancels an order that was never paid, giving back stock, the coupon use and the design lock. */
    @Transactional
    public void cancelUnpaid(Long orderId) {
        Instant now = clock.instant();
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getOrderStatus() != Order.Status.PENDING
                || order.getPaymentStatus() == Order.PaymentStatus.SUCCESS) {
            return;
        }
        stock.release(orderId, InventoryReservation.Status.RELEASED, now);
        String from = order.getOrderStatus().name();
        order.setOrderStatus(Order.Status.CANCELLED);
        if (order.getPaymentStatus() != Order.PaymentStatus.FAILED) {
            order.setPaymentStatus(Order.PaymentStatus.CANCELLED);
        }
        order.setCancelledAt(now);
        payments.findByOrderIdOrderByAttemptNoAsc(orderId).stream()
                .filter(p -> p.getStatus() == Order.PaymentStatus.CREATED || p.getStatus() == Order.PaymentStatus.PENDING)
                .forEach(p -> p.setStatus(Order.PaymentStatus.CANCELLED));
        order.setCancelReason("Not paid within " + settings.getInt("order.unpaid_cancel_hours") + " hours");

        redemptions.findByOrderId(orderId).ifPresent(redemption -> {
            coupons.findByIdForUpdate(redemption.getCouponId())
                    .ifPresent(c -> c.setUsedCount(Math.max(0, c.getUsedCount() - 1)));
            redemptions.delete(redemption);
        });
        for (OrderItem item : order.getItems()) {
            if (item.getCustomDesign() != null
                    && orders.countOtherOpenOrdersWithDesign(item.getCustomDesign().getId(), orderId) == 0) {
                item.getCustomDesign().setLocked(false);
                item.getCustomDesign().setStatus(com.shop.entity.CustomDesign.Status.DRAFT);
            }
        }
        recordChange(order, from, now, order.getCancelReason());
        log.info("Order {} cancelled: not paid in time", order.getOrderNumber());
    }

    private void record(Order order, String status, Instant at, String note) {
        recordChange(order, status, at, note, status);
    }

    private void recordChange(Order order, String from, Instant at, String note) {
        recordChange(order, from, at, note, order.getOrderStatus().name());
    }

    private void recordChange(Order order, String from, Instant at, String note, String to) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(order.getId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setNote(note);
        h.setCreatedAt(at);
        history.save(h);
    }
}
