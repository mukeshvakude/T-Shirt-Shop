package com.shop.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shop.entity.InventoryReservation;
import com.shop.entity.Order;
import com.shop.entity.OrderItem;
import com.shop.entity.Payment;
import com.shop.entity.PaymentTransaction;
import com.shop.exception.ConflictException;
import com.shop.exception.PaymentGatewayException;
import com.shop.payment.RazorpayGateway;
import com.shop.payment.RazorpayGateway.GatewayPayment;
import com.shop.repository.InventoryReservationRepository;
import com.shop.repository.PaymentRepository;
import com.shop.repository.PaymentTransactionRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place where a Razorpay payment changes an order (architecture section 6). Used by the
 * browser verification, webhooks and reconciliation alike, so the same payment is applied exactly
 * once whichever arrives first. Callers hold the order row lock.
 *
 * <ul>
 * <li>Amount, currency and Razorpay order must match the attempt, otherwise it is flagged for review.</li>
 * <li>Authorized payments are captured.</li>
 * <li>A second payment for an already paid order, a payment for a cancelled order, and a payment
 * whose items sold out are refunded in full automatically.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = {ConflictException.class, PaymentGatewayException.class})
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    public enum Result { CONFIRMED, ALREADY_APPLIED, REFUNDED, FAILED_RECORDED, NEEDS_REVIEW, IGNORED }

    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final InventoryReservationRepository reservations;
    private final StockService stock;
    private final OrderLifecycle lifecycle;
    private final RazorpayGateway gateway;
    private final JsonMapper json;

    public Result apply(Order order, Payment attempt, GatewayPayment gp, String source, Instant now) {
        if ("failed".equals(gp.status())) {
            log(attempt, "FAILED", gp.id(), attempt.getAmount(), "FAILED", source, gp.raw(), now);
            if (attempt.getStatus() != Order.PaymentStatus.SUCCESS && attempt.getStatus() != Order.PaymentStatus.REFUNDED) {
                attempt.setStatus(Order.PaymentStatus.FAILED);
                attempt.setFailureCode(cut(gp.errorCode(), 80));
                attempt.setFailureReason(cut(gp.errorDescription(), 300));
                if (order.getPaymentStatus() != Order.PaymentStatus.SUCCESS && order.getPaymentStatus() != Order.PaymentStatus.REFUNDED) {
                    order.setPaymentStatus(Order.PaymentStatus.FAILED);
                }
            }
            return Result.FAILED_RECORDED;
        }
        if (!gp.moneyReceived()) {
            return Result.IGNORED;
        }
        if (!attempt.getRazorpayOrderId().equals(gp.orderId()) || gp.amountPaise() != paise(attempt.getAmount())
                || !attempt.getCurrency().equalsIgnoreCase(gp.currency())) {
            attempt.setNeedsReview(true);
            log(attempt, "MISMATCH", gp.id(), attempt.getAmount(), "NEEDS_REVIEW", source, gp.raw(), now);
            log.error("Payment {} does not match order {} (amount {} {}, Razorpay order {}); flagged for review", gp.id(),
                    order.getOrderNumber(), gp.amountPaise(), gp.currency(), gp.orderId());
            return Result.NEEDS_REVIEW;
        }
        if (gp.id().equals(attempt.getRazorpayPaymentId())
                && (attempt.getStatus() == Order.PaymentStatus.SUCCESS || attempt.getStatus() == Order.PaymentStatus.REFUNDED)) {
            return Result.ALREADY_APPLIED;
        }

        // Another payment already paid this order: this one is a duplicate.
        Payment paid = payments.findByOrderIdOrderByAttemptNoAsc(order.getId()).stream()
                .filter(p -> p.getStatus() == Order.PaymentStatus.SUCCESS || p.getStatus() == Order.PaymentStatus.REFUNDED)
                .filter(p -> p.getRazorpayPaymentId() != null && !p.getRazorpayPaymentId().equals(gp.id()))
                .findFirst().orElse(null);
        if (paid != null) {
            if ("captured".equals(gp.status())) {
                refundPayment(order, attempt, gp.id(), attempt.getAmount(), "Duplicate payment for an order that was already paid",
                        source, now, false);
            } else {
                log(attempt, "DUPLICATE", gp.id(), attempt.getAmount(), "NOT_CAPTURED", source, gp.raw(), now);
            }
            return Result.REFUNDED;
        }

        GatewayPayment money = gp;
        if ("authorized".equals(gp.status())) {
            money = gateway.capture(gp.id(), gp.amountPaise(), gp.currency());
            log(attempt, "CAPTURED", gp.id(), attempt.getAmount(), money.status(), source, money.raw(), now);
        } else {
            log(attempt, "CAPTURED", gp.id(), attempt.getAmount(), gp.status(), source, gp.raw(), now);
        }
        attempt.setStatus(Order.PaymentStatus.SUCCESS);
        attempt.setRazorpayPaymentId(gp.id());
        attempt.setMethod(cut(money.method() != null ? money.method() : gp.method(), 30));
        attempt.setMethodDetail(cut(money.methodDetail() != null ? money.methodDetail() : gp.methodDetail(), 100));
        attempt.setPaidAt(now);
        attempt.setFailureCode(null);
        attempt.setFailureReason(null);
        order.setPaymentStatus(Order.PaymentStatus.SUCCESS);

        if (order.getOrderStatus() == Order.Status.CANCELLED) {
            refund(order, attempt, "Payment arrived after the order was cancelled", source, now);
            return Result.REFUNDED;
        }
        if (order.getOrderStatus() != Order.Status.PENDING) {
            return Result.ALREADY_APPLIED;
        }
        if (!takeStock(order, now)) {
            lifecycle.cancel(order, "An item sold out before the payment arrived; the full amount is being refunded", null, now);
            refund(order, attempt, "Items sold out before the payment arrived", source, now);
            return Result.REFUNDED;
        }
        order.setOrderStatus(Order.Status.CONFIRMED);
        order.setConfirmedAt(now);
        order.setReservationExpiresAt(null);
        lifecycle.record(order, Order.Status.PENDING.name(), "Paid online (Razorpay payment " + gp.id() + ")", null, now);
        log.info("Order {} confirmed by {} payment {}", order.getOrderNumber(), source, gp.id());
        return Result.CONFIRMED;
    }

    /** Full refund of the attempt's payment. A gateway failure flags the payment for a retry. */
    public void refund(Order order, Payment payment, String reason, String source, Instant now) {
        if (payment.getRefundedAmount().compareTo(payment.getAmount()) >= 0) {
            return;
        }
        refundPayment(order, payment, payment.getRazorpayPaymentId(), payment.getAmount(), reason, source, now, true);
    }

    private void refundPayment(Order order, Payment payment, String paymentId, BigDecimal amount, String reason, String source,
            Instant now, boolean ownPayment) {
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("order_number", order.getOrderNumber());
        notes.put("reason", cut(reason, 200));
        try {
            RazorpayGateway.GatewayRefund refund = gateway.refund(paymentId, paise(amount),
                    cut("rf-" + order.getOrderNumber() + "-" + payment.getAttemptNo() + (ownPayment ? "" : "-dup"), 40), notes);
            log(payment, "REFUND_INITIATED", refund.id(), amount, refund.status(), source,
                    Map.of("refund_id", refund.id(), "payment_id", paymentId, "reason", reason), now);
            if (ownPayment) {
                payment.setRefundedAmount(amount);
                payment.setStatus(Order.PaymentStatus.REFUNDED);
                order.setPaymentStatus(Order.PaymentStatus.REFUNDED);
                payment.setNeedsReview(false);
            }
            lifecycle.record(order, order.getOrderStatus().name(), "Refund of ₹" + amount.toPlainString() + " started ("
                    + refund.id() + "): " + reason, null, now);
            log.info("Order {}: refund {} for payment {} ({})", order.getOrderNumber(), refund.id(), paymentId, reason);
        } catch (PaymentGatewayException ex) {
            payment.setNeedsReview(true);
            log(payment, "REFUND_INITIATED", paymentId, amount, "FAILED", source, Map.of("reason", reason, "error", ex.getMessage()), now);
            log.error("Order {}: refund of payment {} failed and will be retried: {}", order.getOrderNumber(), paymentId, ex.getMessage());
        }
    }

    /** Commits the active hold, or takes the stock now when the hold ended. False when an item sold out. */
    private boolean takeStock(Order order, Instant now) {
        if (!reservations.findByOrderIdAndStatus(order.getId(), InventoryReservation.Status.ACTIVE).isEmpty()) {
            stock.commit(order.getId(), now);
            return true;
        }
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            quantities.merge(item.getInventoryId(), item.getQuantity(), Integer::sum);
        }
        try {
            stock.hold(order.getId(), quantities, now, true, now);
            return true;
        } catch (ConflictException ex) {
            return false;
        }
    }

    public void log(Payment payment, String type, String reference, BigDecimal amount, String status, String source,
            Map<String, ?> raw, Instant at) {
        PaymentTransaction t = new PaymentTransaction();
        t.setPaymentId(payment.getId());
        t.setType(type);
        t.setGatewayReference(cut(reference, 80));
        t.setAmount(amount);
        t.setStatus(cut(status == null ? "UNKNOWN" : status.toUpperCase(java.util.Locale.ROOT), 30));
        t.setSource(source);
        t.setRawResponse(raw == null ? null : json.writeValueAsString(raw));
        t.setCreatedAt(at);
        transactions.save(t);
    }

    static long paise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
