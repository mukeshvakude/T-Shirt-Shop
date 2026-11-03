package com.shop.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.entity.Order;
import com.shop.entity.Payment;
import com.shop.payment.RazorpayGateway;
import com.shop.payment.RazorpayGateway.GatewayPayment;
import com.shop.repository.OrderRepository;
import com.shop.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Catches payments whose browser confirmation and webhook were both lost (architecture section 7.5):
 * asks Razorpay about unconfirmed attempts from the last two days, and retries refunds that failed.
 */
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    static final Duration LOOK_BACK = Duration.ofHours(48);
    /** Leave the customer time to finish paying before asking Razorpay. */
    static final Duration GRACE = Duration.ofMinutes(2);
    static final int BATCH = 50;

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final PaymentProcessor processor;
    private final RazorpayGateway gateway;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> attemptsToCheck() {
        Instant now = clock.instant();
        return payments.findForReconciliation(
                List.of(Order.PaymentStatus.CREATED, Order.PaymentStatus.PENDING, Order.PaymentStatus.FAILED,
                        Order.PaymentStatus.CANCELLED),
                now.minus(LOOK_BACK), now.minus(GRACE), PageRequest.of(0, BATCH));
    }

    @Transactional(readOnly = true)
    public List<Long> refundsToRetry() {
        return payments.findRefundsToRetry();
    }

    /** Applies any money Razorpay received for this attempt (same rules as verification and webhooks). */
    @Transactional
    public void reconcile(Long paymentId) {
        Instant now = clock.instant();
        Payment attempt = payments.findById(paymentId).orElse(null);
        if (attempt == null || attempt.getRazorpayOrderId() == null) {
            return;
        }
        List<GatewayPayment> found = gateway.fetchOrderPayments(attempt.getRazorpayOrderId());
        List<GatewayPayment> received = found.stream().filter(GatewayPayment::moneyReceived).toList();
        if (received.isEmpty()) {
            return;
        }
        Order order = orders.findByIdForUpdate(attempt.getOrderId()).orElseThrow();
        for (GatewayPayment gp : received) {
            PaymentProcessor.Result result = processor.apply(order, attempt, gp, "RECONCILE", now);
            log.info("Reconciled payment {} of order {}: {}", gp.id(), order.getOrderNumber(), result);
        }
    }

    @Transactional
    public void retryRefund(Long paymentId) {
        Payment payment = payments.findById(paymentId).orElse(null);
        if (payment == null) return;
        Order order = orders.findByIdForUpdate(payment.getOrderId()).orElseThrow();
        processor.refund(order, payment, order.getCancelReason() == null ? "Order cancelled" : order.getCancelReason(),
                "RECONCILE", clock.instant());
    }
}
