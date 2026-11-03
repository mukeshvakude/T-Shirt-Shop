package com.shop.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.config.AppProperties;
import com.shop.dto.order.OrderResponse;
import com.shop.dto.payment.PaymentFailedRequest;
import com.shop.dto.payment.PaymentStartRequest;
import com.shop.dto.payment.PaymentStartResponse;
import com.shop.dto.payment.VerifyPaymentRequest;
import com.shop.entity.InventoryReservation;
import com.shop.entity.Order;
import com.shop.entity.OrderItem;
import com.shop.entity.OrderStatusHistory;
import com.shop.entity.Payment;
import com.shop.entity.PaymentTransaction;
import com.shop.exception.ApiException;
import com.shop.exception.BadRequestException;
import com.shop.exception.BusinessRuleException;
import com.shop.exception.PaymentGatewayException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.payment.RazorpayGateway;
import com.shop.repository.InventoryReservationRepository;
import com.shop.repository.OrderRepository;
import com.shop.repository.OrderStatusHistoryRepository;
import com.shop.repository.PaymentRepository;
import com.shop.repository.PaymentTransactionRepository;
import com.shop.security.RateLimiter;
import com.shop.service.AppSettingService;
import com.shop.service.OrderService;
import com.shop.service.PaymentService;
import com.shop.util.SecureTokens;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Razorpay Standard Checkout (architecture section 6). The server creates the Razorpay order with
 * the order total, the browser collects the payment, and the order is confirmed only after the
 * HMAC-SHA256 signature over "razorpay_order_id|razorpay_payment_id" checks out with the Key Secret.
 * Webhooks, gateway re-checks, automatic refunds and reconciliation follow in Phase 14.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    static final int STARTS_PER_HOUR = 15;

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final InventoryReservationRepository reservations;
    private final OrderStatusHistoryRepository history;
    private final StockService stock;
    private final OrderService orderService;
    private final RazorpayGateway gateway;
    private final PaymentProcessor processor;
    private final AppSettingService settings;
    private final AppProperties properties;
    private final RateLimiter rateLimiter;
    private final JsonMapper json;
    private final Clock clock;

    // ------------------------------------------------------------------ start / retry

    @Override
    public PaymentStartResponse start(Long userId, PaymentStartRequest r) {
        AppProperties.Payments config = properties.payments();
        if (config == null || !config.razorpayConfigured()) {
            throw new BusinessRuleException("ONLINE_PAYMENT_UNAVAILABLE", "Online payment is not available yet.");
        }
        rateLimiter.check("payment-start:" + userId, STARTS_PER_HOUR, Duration.ofHours(1));
        Instant now = clock.instant();
        Order order = ownedForUpdate(userId, r.orderNumber());
        requirePayable(order, now);

        // Hold the stock again when the earlier hold ended (fails with OUT_OF_STOCK if it sold out).
        Instant holdUntil = order.getReservationExpiresAt();
        if (reservations.findByOrderIdAndStatus(order.getId(), InventoryReservation.Status.ACTIVE).isEmpty()) {
            holdUntil = now.plus(Duration.ofMinutes(settings.getInt("stock.reservation_minutes")));
            stock.hold(order.getId(), quantities(order), holdUntil, false, now);
            order.setReservationExpiresAt(holdUntil);
            record(order, "Items held again for payment");
        }

        // Reuse the current attempt while it can still be paid; otherwise create a new Razorpay order.
        Payment payment = payments.findTopByOrderIdOrderByAttemptNoDesc(order.getId())
                .filter(p -> p.getStatus() == Order.PaymentStatus.CREATED || p.getStatus() == Order.PaymentStatus.PENDING)
                .orElse(null);
        if (payment == null) {
            int attempt = payments.findTopByOrderIdOrderByAttemptNoDesc(order.getId()).map(p -> p.getAttemptNo() + 1).orElse(1);
            Map<String, String> notes = new LinkedHashMap<>();
            notes.put("order_number", order.getOrderNumber());
            notes.put("attempt", String.valueOf(attempt));
            RazorpayGateway.GatewayOrder created = gateway.createOrder(paise(order.getGrandTotal()), order.getCurrency(),
                    order.getOrderNumber(), notes);
            if (created.id() == null || created.id().isBlank() || created.amountPaise() != paise(order.getGrandTotal())) {
                throw new PaymentGatewayException("Online payment could not be started. Please try again.");
            }
            payment = new Payment();
            payment.setOrderId(order.getId());
            payment.setAttemptNo(attempt);
            payment.setRazorpayOrderId(created.id());
            payment.setAmount(order.getGrandTotal());
            payment.setCurrency(order.getCurrency());
            payment.setStatus(Order.PaymentStatus.CREATED);
            payments.saveAndFlush(payment);
            transaction(payment, "ORDER_CREATED", created.id(), order.getGrandTotal(), created.status(), "SYSTEM",
                    Map.of("razorpay_order_id", created.id(), "amount", created.amountPaise(), "currency", created.currency()), now);
        }
        if (order.getPaymentStatus() != Order.PaymentStatus.PENDING) {
            order.setPaymentStatus(Order.PaymentStatus.PENDING);
        }
        log.info("Payment started for order {} (attempt {}, {})", order.getOrderNumber(), payment.getAttemptNo(),
                payment.getRazorpayOrderId());
        return new PaymentStartResponse(config.razorpayKeyId(), payment.getRazorpayOrderId(), paise(order.getGrandTotal()),
                order.getCurrency(), order.getOrderNumber(), "Order " + order.getOrderNumber(),
                new PaymentStartResponse.Prefill(order.getContactName(), order.getContactEmail(), order.getContactPhone()),
                holdUntil);
    }

    // ------------------------------------------------------------------ verify

    /** Refusals (bad signature, mismatch) keep their audit entry: they do not roll back. */
    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public OrderResponse verify(Long userId, VerifyPaymentRequest r) {
        Instant now = clock.instant();
        Order order = ownedForUpdate(userId, r.orderNumber());
        Payment payment = payments.findByRazorpayOrderId(r.razorpayOrderId())
                .filter(p -> p.getOrderId().equals(order.getId()))
                .orElseThrow(() -> new BadRequestException("PAYMENT_MISMATCH", "This payment does not belong to the order."));

        // Same successful payment reported again (double click, reload): nothing more to do.
        if (payment.getStatus() == Order.PaymentStatus.SUCCESS || payment.getStatus() == Order.PaymentStatus.REFUNDED) {
            if (r.razorpayPaymentId().equals(payment.getRazorpayPaymentId())) {
                return orderService.toResponse(order);
            }
            throw new BadRequestException("PAYMENT_MISMATCH", "This order is already paid.");
        }

        String secret = properties.payments() == null ? null : properties.payments().razorpayKeySecret();
        String expected = secret == null ? null : SecureTokens.hmacSha256Hex(secret, r.razorpayOrderId() + "|" + r.razorpayPaymentId());
        if (!SecureTokens.constantTimeEquals(expected, r.razorpaySignature().toLowerCase(java.util.Locale.ROOT))) {
            transaction(payment, "VERIFY", r.razorpayPaymentId(), payment.getAmount(), "SIGNATURE_INVALID", "CLIENT_VERIFY",
                    Map.of("razorpay_payment_id", r.razorpayPaymentId()), now);
            log.warn("Invalid Razorpay signature for order {} payment {}", order.getOrderNumber(), r.razorpayPaymentId());
            throw new BadRequestException("INVALID_SIGNATURE", "We could not confirm this payment. If money was taken, it will be "
                    + "checked automatically; otherwise please try paying again.");
        }

        payment.setRazorpaySignature(r.razorpaySignature().toLowerCase(java.util.Locale.ROOT));
        transaction(payment, "VERIFY", r.razorpayPaymentId(), payment.getAmount(), "SIGNATURE_VALID", "CLIENT_VERIFY",
                Map.of("razorpay_payment_id", r.razorpayPaymentId()), now);

        // The signature proves Razorpay issued these ids; the payment itself is read from Razorpay.
        RazorpayGateway.GatewayPayment gp;
        try {
            gp = gateway.fetchPayment(r.razorpayPaymentId());
        } catch (PaymentGatewayException ex) {
            throw new PaymentGatewayException("Your payment reached Razorpay but could not be confirmed yet. "
                    + "It will be confirmed automatically within a few minutes; please do not pay again.");
        }
        if (!r.razorpayOrderId().equals(gp.orderId())) {
            throw new BadRequestException("PAYMENT_MISMATCH", "This payment does not belong to the order.");
        }
        processor.apply(order, payment, gp, "CLIENT_VERIFY", now);
        return orderService.toResponse(order);
    }

    // ------------------------------------------------------------------ failures

    @Override
    public OrderResponse failed(Long userId, PaymentFailedRequest r) {
        Instant now = clock.instant();
        Order order = ownedForUpdate(userId, r.orderNumber());
        Payment payment = payments.findByRazorpayOrderId(r.razorpayOrderId())
                .filter(p -> p.getOrderId().equals(order.getId()))
                .orElseThrow(() -> new BadRequestException("PAYMENT_MISMATCH", "This payment does not belong to the order."));
        if (payment.getStatus() != Order.PaymentStatus.SUCCESS) {
            payment.setStatus(Order.PaymentStatus.FAILED);
            payment.setFailureCode(trim(r.code(), 80));
            payment.setFailureReason(trim(r.description(), 300));
            if (order.getPaymentStatus() != Order.PaymentStatus.SUCCESS) {
                order.setPaymentStatus(Order.PaymentStatus.FAILED);
            }
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("code", r.code());
            report.put("description", r.description());
            report.put("razorpay_payment_id", r.razorpayPaymentId());
            transaction(payment, "FAILED", r.razorpayPaymentId(), payment.getAmount(), "FAILED", "CLIENT_VERIFY", report, now);
            log.info("Payment attempt {} for order {} failed: {}", payment.getAttemptNo(), order.getOrderNumber(), r.code());
        }
        return orderService.toResponse(order);
    }

    // ------------------------------------------------------------------ helpers

    private Order ownedForUpdate(Long userId, String orderNumber) {
        Order found = orders.findByNumberWithItems(orderNumber)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
        return orders.findByIdForUpdate(found.getId()).orElseThrow();
    }

    private void requirePayable(Order order, Instant now) {
        if (order.getPaymentMethod() != Order.PaymentMethod.RAZORPAY) {
            throw new BusinessRuleException("NOT_ONLINE_ORDER", "This order is paid with Cash on Delivery.");
        }
        if (order.getPaymentStatus() == Order.PaymentStatus.SUCCESS) {
            throw new BusinessRuleException("ALREADY_PAID", "This order is already paid.");
        }
        Duration window = Duration.ofHours(settings.getInt("order.unpaid_cancel_hours"));
        if (order.getOrderStatus() != Order.Status.PENDING || !order.getPlacedAt().plus(window).isAfter(now)) {
            throw new BusinessRuleException("ORDER_NOT_PAYABLE", "This order can no longer be paid. Please place a new order.");
        }
    }

    private static Map<Long, Integer> quantities(Order order) {
        Map<Long, Integer> q = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            q.merge(item.getInventoryId(), item.getQuantity(), Integer::sum);
        }
        return q;
    }

    static long paise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    private void transaction(Payment payment, String type, String reference, BigDecimal amount, String status, String source,
            Map<String, ?> raw, Instant at) {
        PaymentTransaction t = new PaymentTransaction();
        t.setPaymentId(payment.getId());
        t.setType(type);
        t.setGatewayReference(reference);
        t.setAmount(amount);
        t.setStatus(status);
        t.setSource(source);
        t.setRawResponse(json.writeValueAsString(raw));
        t.setCreatedAt(at);
        transactions.save(t);
    }

    private void record(Order order, String note) {
        recordChange(order, order.getOrderStatus().name(), note);
    }

    private void recordChange(Order order, String from, String note) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(order.getId());
        h.setFromStatus(from);
        h.setToStatus(order.getOrderStatus().name());
        h.setNote(note);
        h.setCreatedAt(clock.instant());
        history.save(h);
    }

    private static String trim(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
