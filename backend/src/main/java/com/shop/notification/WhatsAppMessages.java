package com.shop.notification;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.shop.config.AppProperties;
import com.shop.dto.order.OrderResponse;
import com.shop.util.MoneyUtils;

/**
 * The wording of every WhatsApp order message, in one place. Each message has the Meta template
 * name and its {{1}}, {{2}}... values (for automatic sending through the Cloud API) and the same
 * text written out in full (for the admin's one-click "Send on WhatsApp" links). The template
 * bodies to create in WhatsApp Manager are listed in docs/WHATSAPP.md and must match {@link #text}.
 */
@Component
public class WhatsAppMessages {

    public record Message(String type, String label, String template, List<String> params, String text) {
    }

    private final String shop;
    private final String siteUrl;

    public WhatsAppMessages(AppProperties properties) {
        // Used inside String.format patterns, so a % in the name is escaped
        this.shop = (properties.business() == null ? "T-Shirt Shop" : properties.business().displayName()).replace("%", "%%");
        String url = properties.frontendUrl();
        this.siteUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public Message forEvent(OrderResponse o, OrderEvent.Type type, BigDecimal amount) {
        return switch (type) {
            case CONFIRMED -> confirmed(o);
            case SHIPPED -> shipped(o);
            case OUT_FOR_DELIVERY -> outForDelivery(o);
            case DELIVERED -> delivered(o);
            case CANCELLED -> cancelled(o);
            case REFUND_STARTED -> refund(o, amount != null ? amount : o.total());
        };
    }

    /** Messages that fit the order's current state, for the admin order page. */
    public List<Message> forAdmin(OrderResponse o) {
        Message status = switch (o.status()) {
            case "CONFIRMED", "PROCESSING" -> confirmed(o);
            case "SHIPPED" -> shipped(o);
            case "OUT_FOR_DELIVERY" -> outForDelivery(o);
            case "DELIVERED" -> delivered(o);
            case "CANCELLED" -> cancelled(o);
            default -> null;
        };
        Message hello = new Message("HELLO", "Message about this order", null, List.of(),
                "Hi " + firstName(o) + ", this is " + shop.replace("%%", "%") + " about your order " + o.orderNumber() + ". ");
        return status == null ? List.of(hello) : List.of(status, hello);
    }

    public Message confirmed(OrderResponse o) {
        String pay = "COD".equals(o.paymentMethod()) ? "Please keep " + money(o.total()) + " ready in cash at delivery."
                : "We have received your payment of " + money(o.total()) + ".";
        return message("CONFIRMED", "Order confirmed", "order_confirmed", o,
                "Hi %1$s, thank you for shopping with " + shop + "! Your order %2$s is confirmed. %3$s Track it at %4$s anytime.",
                firstName(o), o.orderNumber(), pay, link(o));
    }

    public Message shipped(OrderResponse o) {
        String courier = o.courierName() == null ? "our courier partner"
                : o.courierName() + (o.trackingNumber() == null ? "" : " (tracking " + o.trackingNumber() + ")");
        return message("SHIPPED", "Shipped", "order_shipped", o,
                "Hi %1$s, your " + shop + " order %2$s has shipped with %3$s. Track it at %4$s anytime.",
                firstName(o), o.orderNumber(), courier, link(o));
    }

    public Message outForDelivery(OrderResponse o) {
        String pay = "COD".equals(o.paymentMethod()) ? "Please keep " + money(o.total()) + " ready in cash."
                : "It should reach you today.";
        return message("OUT_FOR_DELIVERY", "Out for delivery", "order_out_for_delivery", o,
                "Hi %1$s, your " + shop + " order %2$s is out for delivery. %3$s Thank you for shopping with us.",
                firstName(o), o.orderNumber(), pay);
    }

    public Message delivered(OrderResponse o) {
        return message("DELIVERED", "Delivered", "order_delivered", o,
                "Hi %1$s, your " + shop + " order %2$s has been delivered. We hope you love it! Your invoice is at %3$s anytime.",
                firstName(o), o.orderNumber(), link(o));
    }

    public Message cancelled(OrderResponse o) {
        String refund = "REFUNDED".equals(o.paymentStatus())
                ? "Your payment of " + money(o.total()) + " is being refunded and usually shows in 5 to 7 working days."
                : "No payment was taken.";
        return message("CANCELLED", "Cancelled", "order_cancelled", o,
                "Hi %1$s, your " + shop + " order %2$s has been cancelled. %3$s Questions? Just reply to this message.",
                firstName(o), o.orderNumber(), refund);
    }

    public Message refund(OrderResponse o, BigDecimal amount) {
        return message("REFUND_STARTED", "Refund started", "order_refund", o,
                "Hi %1$s, we have refunded %3$s for your " + shop + " order %2$s. Banks usually show it in 5 to 7 working days.",
                firstName(o), o.orderNumber(), money(amount));
    }

    /** New-order alert for the shop owner. */
    public Message newOrderForShop(OrderResponse o) {
        String method = "COD".equals(o.paymentMethod()) ? "Cash on Delivery" : "paid online";
        return message("NEW_ORDER", "New order", "new_order_alert", o,
                "New order %1$s from %2$s: %3$s, %4$s. Open the admin portal to process it.",
                o.orderNumber(), o.contact().name(), money(o.total()), method);
    }

    private Message message(String type, String label, String template, OrderResponse o, String format, String... values) {
        List<String> params = java.util.Arrays.stream(values).map(WhatsAppMessages::param).toList();
        return new Message(type, label, template, params, String.format(format, params.toArray()));
    }

    /** Meta rejects template values with new lines, tabs or more than four spaces in a row. */
    static String param(String value) {
        String v = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").trim();
        return v.length() > 300 ? v.substring(0, 300) : v;
    }

    private String link(OrderResponse o) {
        return siteUrl + "/order/" + o.orderNumber();
    }

    private static String firstName(OrderResponse o) {
        String name = o.contact().name() == null ? "" : o.contact().name().trim();
        int space = name.indexOf(' ');
        return name.isEmpty() ? "there" : space > 0 ? name.substring(0, space) : name;
    }

    private static String money(BigDecimal amount) {
        return "₹" + MoneyUtils.groupIndian(amount, false);
    }
}
