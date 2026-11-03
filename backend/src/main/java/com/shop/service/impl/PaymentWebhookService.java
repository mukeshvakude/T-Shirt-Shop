package com.shop.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shop.config.AppProperties;
import com.shop.entity.Order;
import com.shop.entity.Payment;
import com.shop.entity.PaymentWebhookEvent;
import com.shop.exception.BadRequestException;
import com.shop.exception.BusinessRuleException;
import com.shop.payment.RazorpayGateway.GatewayPayment;
import com.shop.payment.RazorpayHttpGateway;
import com.shop.repository.OrderRepository;
import com.shop.repository.PaymentRepository;
import com.shop.repository.PaymentWebhookEventRepository;
import com.shop.util.SecureTokens;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Razorpay webhooks. The X-Razorpay-Signature header must be HMAC-SHA256 of the raw body with the
 * webhook secret. Each event id is stored once; deliveries of an event that was already handled
 * are acknowledged without doing anything again. Steps run in separate transactions so a failure
 * is recorded and the event can be retried by Razorpay.
 */
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentWebhookEventRepository events;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final PaymentProcessor processor;
    private final AppProperties properties;
    private final JsonMapper json;
    private final Clock clock;

    /** Rejects deliveries that were not signed with our webhook secret. */
    public void verifySignature(byte[] body, String signature) {
        AppProperties.Payments config = properties.payments();
        if (config == null || !config.webhookConfigured()) {
            throw new BusinessRuleException("WEBHOOK_NOT_CONFIGURED", "Webhooks are not configured.");
        }
        String expected = SecureTokens.hmacSha256Hex(config.razorpayWebhookSecret(), new String(body, StandardCharsets.UTF_8));
        if (signature == null || !SecureTokens.constantTimeEquals(expected, signature.trim().toLowerCase(java.util.Locale.ROOT))) {
            log.warn("Rejected a webhook with an invalid signature");
            throw new BadRequestException("INVALID_SIGNATURE", "Invalid webhook signature.");
        }
    }

    /**
     * Stores the event. Returns its id when it still has to be processed, or empty when the same
     * event was already handled.
     */
    @Transactional
    public Optional<Long> record(byte[] body, String eventIdHeader) {
        String raw = new String(body, StandardCharsets.UTF_8);
        JsonNode root = json.readTree(raw);
        String eventId = eventIdHeader != null && !eventIdHeader.isBlank() ? eventIdHeader.trim()
                : "sha256:" + SecureTokens.sha256Hex(raw).substring(0, 56);
        Optional<PaymentWebhookEvent> existing = events.findByEventId(eventId);
        if (existing.isPresent()) {
            PaymentWebhookEvent e = existing.get();
            return e.getStatus() == PaymentWebhookEvent.Status.PROCESSED || e.getStatus() == PaymentWebhookEvent.Status.IGNORED
                    ? Optional.empty() : Optional.of(e.getId());
        }
        PaymentWebhookEvent e = new PaymentWebhookEvent();
        e.setEventId(cut(eventId, 64));
        e.setEventType(cut(root.path("event").asString("unknown"), 60));
        JsonNode payment = root.path("payload").path("payment").path("entity");
        if (payment.isObject()) {
            e.setRazorpayPaymentId(cut(text(payment, "id"), 50));
            e.setRazorpayOrderId(cut(text(payment, "order_id"), 50));
        }
        e.setPayload(raw);
        e.setReceivedAt(clock.instant());
        try {
            events.saveAndFlush(e);
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty(); // the same event arrived twice at the same moment
        }
        return Optional.of(e.getId());
    }

    /** Applies the event. Payment events go through the same processor as browser verification. */
    @Transactional
    public void process(Long eventRowId) {
        Instant now = clock.instant();
        PaymentWebhookEvent event = events.findById(eventRowId).orElseThrow();
        JsonNode root = json.readTree(event.getPayload());
        String type = event.getEventType();
        PaymentWebhookEvent.Status outcome = PaymentWebhookEvent.Status.IGNORED;
        switch (type) {
            case "payment.authorized", "payment.captured", "payment.failed", "order.paid" -> {
                JsonNode entity = root.path("payload").path("payment").path("entity");
                if (entity.isObject()) {
                    GatewayPayment gp = RazorpayHttpGateway.payment(entity);
                    Optional<Payment> attempt = gp.orderId() == null ? Optional.empty() : payments.findByRazorpayOrderId(gp.orderId());
                    if (attempt.isPresent()) {
                        Order order = orders.findByIdForUpdate(attempt.get().getOrderId()).orElseThrow();
                        event.setPaymentId(attempt.get().getId());
                        PaymentProcessor.Result result = processor.apply(order, attempt.get(), gp, "WEBHOOK", now);
                        outcome = result == PaymentProcessor.Result.IGNORED ? PaymentWebhookEvent.Status.IGNORED
                                : PaymentWebhookEvent.Status.PROCESSED;
                        log.info("Webhook {} {} for order {}: {}", event.getEventId(), type, order.getOrderNumber(), result);
                    }
                }
            }
            case "refund.processed", "refund.failed" -> {
                JsonNode refund = root.path("payload").path("refund").path("entity");
                Optional<Payment> payment = payments.findByRazorpayPaymentId(text(refund, "payment_id"));
                if (payment.isPresent()) {
                    Payment p = payment.get();
                    event.setPaymentId(p.getId());
                    boolean failed = type.equals("refund.failed");
                    if (failed) p.setNeedsReview(true);
                    processor.log(p, failed ? "REFUND_INITIATED" : "REFUNDED", text(refund, "id"),
                            p.getAmount(), failed ? "FAILED" : "PROCESSED", "WEBHOOK",
                            Map.of("refund_id", String.valueOf(text(refund, "id")), "status", String.valueOf(text(refund, "status"))), now);
                    outcome = PaymentWebhookEvent.Status.PROCESSED;
                }
            }
            default -> log.debug("Webhook {} of type {} ignored", event.getEventId(), type);
        }
        event.setStatus(outcome);
        event.setProcessedAt(now);
        event.setProcessingError(null);
    }

    /** Records a processing failure; Razorpay retries the delivery and processing runs again. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long eventRowId, Exception error) {
        events.findById(eventRowId).ifPresent(e -> {
            e.setStatus(PaymentWebhookEvent.Status.FAILED);
            e.setProcessingError(cut(error.getClass().getSimpleName() + ": " + error.getMessage(), 500));
        });
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
