package com.shop.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.shop.config.AppProperties;
import com.shop.config.WhatsAppProperties;
import com.shop.dto.order.OrderResponse;
import com.shop.repository.OrderRepository;
import com.shop.service.OrderService;

/**
 * Turns order and contact events into emails, and WhatsApp messages for orders where the customer
 * asked for them, once the change is committed (a rolled-back action never messages anyone). A
 * cancellation that includes a refund sends one message, not two.
 */
@Component
public class NotificationListener {

    private static final Duration SAME_ACTION = Duration.ofMinutes(2);

    private final NotificationExecutor executor;
    private final EmailSender email;
    private final EmailTemplates templates;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final TransactionTemplate readOnly;
    private final String shopEmail;
    private final WhatsAppSender whatsApp;
    private final WhatsAppMessages whatsAppMessages;
    private final String ownerWhatsApp;
    private final Map<String, Instant> recentCancellations = new ConcurrentHashMap<>();

    public NotificationListener(NotificationExecutor executor, EmailSender email, EmailTemplates templates, OrderRepository orders,
            OrderService orderService, PlatformTransactionManager tx, AppProperties properties, WhatsAppSender whatsApp,
            WhatsAppMessages whatsAppMessages, WhatsAppProperties whatsAppProperties) {
        this.executor = executor;
        this.email = email;
        this.templates = templates;
        this.whatsApp = whatsApp;
        this.whatsAppMessages = whatsAppMessages;
        this.ownerWhatsApp = whatsAppProperties.ownerDigits();
        this.orders = orders;
        this.orderService = orderService;
        this.readOnly = new TransactionTemplate(tx);
        this.readOnly.setReadOnly(true);
        this.shopEmail = properties.business() == null ? null : properties.business().email();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(OrderEvent event) {
        Instant now = Instant.now();
        recentCancellations.values().removeIf(t -> t.isBefore(now.minus(SAME_ACTION)));
        if (event.type() == OrderEvent.Type.CANCELLED) {
            recentCancellations.put(event.orderNumber(), now);
        } else if (event.type() == OrderEvent.Type.REFUND_STARTED && recentCancellations.containsKey(event.orderNumber())) {
            return; // the cancellation email already explains the refund
        }
        executor.run(() -> send(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ContactReceivedEvent e) {
        if (shopEmail == null || shopEmail.isBlank()) return;
        executor.run(() -> {
            EmailTemplates.Email m = templates.contactForShop(e.name(), e.email(), e.phone(), e.subject(), e.message());
            email.send(shopEmail, m.subject(), m.html(), m.text());
        });
    }

    private void send(OrderEvent event) {
        OrderResponse o = readOnly.execute(s -> orders.findByNumberWithItems(event.orderNumber()).map(orderService::toResponse).orElse(null));
        if (o == null) return;
        EmailTemplates.Email m = switch (event.type()) {
            case CONFIRMED -> templates.orderConfirmed(o);
            case SHIPPED -> templates.shipped(o);
            case OUT_FOR_DELIVERY -> templates.outForDelivery(o);
            case DELIVERED -> templates.delivered(o);
            case CANCELLED -> templates.cancelled(o);
            case REFUND_STARTED -> templates.refundStarted(o, event.amount() != null ? event.amount() : o.total());
        };
        email.send(o.contact().email(), m.subject(), m.html(), m.text());
        if (event.type() == OrderEvent.Type.CONFIRMED && shopEmail != null && !shopEmail.isBlank()) {
            EmailTemplates.Email shop = templates.newOrderForShop(o);
            email.send(shopEmail, shop.subject(), shop.html(), shop.text());
        }
        if (o.contact().whatsappUpdates()) {
            whatsApp.send(o.contact().phone(), whatsAppMessages.forEvent(o, event.type(), event.amount()));
        }
        if (event.type() == OrderEvent.Type.CONFIRMED && ownerWhatsApp != null) {
            whatsApp.send(ownerWhatsApp, whatsAppMessages.newOrderForShop(o));
        }
    }
}
