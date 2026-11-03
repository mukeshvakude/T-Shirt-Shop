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
import com.shop.repository.InventoryReservationRepository;
import com.shop.repository.OrderRepository;
import com.shop.repository.PaymentRepository;
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
    private final PaymentRepository payments;
    private final StockService stock;
    private final OrderLifecycle lifecycle;
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
        lifecycle.record(order, order.getOrderStatus().name(),
                "Payment window ended; held items were released. The order can still be paid if stock allows.", null, now);
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
        if (order.getPaymentStatus() != Order.PaymentStatus.FAILED) {
            order.setPaymentStatus(Order.PaymentStatus.CANCELLED);
        }
        payments.findByOrderIdOrderByAttemptNoAsc(orderId).stream()
                .filter(p -> p.getStatus() == Order.PaymentStatus.CREATED || p.getStatus() == Order.PaymentStatus.PENDING)
                .forEach(p -> p.setStatus(Order.PaymentStatus.CANCELLED));
        lifecycle.cancel(order, "Not paid within " + settings.getInt("order.unpaid_cancel_hours") + " hours", null, now);
        log.info("Order {} cancelled: not paid in time", order.getOrderNumber());
    }
}
