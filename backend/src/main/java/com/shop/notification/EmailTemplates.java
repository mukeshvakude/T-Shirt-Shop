package com.shop.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.shop.config.AppProperties;
import com.shop.dto.order.OrderResponse;

/**
 * Plain, mobile-friendly HTML emails with inline styles (email clients ignore stylesheets) and a
 * text alternative. Every value from the database is HTML-escaped.
 */
@Component
public class EmailTemplates {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    public record Email(String subject, String html, String text) {
    }

    private final String shop;
    private final String siteUrl;
    private final String supportEmail;

    public EmailTemplates(AppProperties properties) {
        this.shop = properties.business() == null ? "T-Shirt Shop" : properties.business().displayName();
        String url = properties.frontendUrl();
        this.siteUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.supportEmail = properties.business() == null ? null : properties.business().email();
    }

    public Email orderConfirmed(OrderResponse o) {
        String intro = "COD".equals(o.paymentMethod())
                ? "Thank you for your order. Please keep " + money(o.total()) + " ready to pay in cash when it arrives."
                : "Thank you for your order. We have received your payment of " + money(o.total()) + ".";
        return order(o, "Order " + o.orderNumber() + " confirmed", "Your order is confirmed", intro, null, true);
    }

    public Email shipped(OrderResponse o) {
        String tracking = o.courierName() == null ? null
                : "Courier: " + o.courierName() + (o.trackingNumber() == null ? "" : " · Tracking number: " + o.trackingNumber());
        return order(o, "Order " + o.orderNumber() + " has shipped", "Your order is on its way",
                "Good news: your order has left our store.", tracking, false);
    }

    public Email outForDelivery(OrderResponse o) {
        return order(o, "Order " + o.orderNumber() + " is out for delivery", "Arriving today",
                "COD".equals(o.paymentMethod())
                        ? "Your order is out for delivery. Please keep " + money(o.total()) + " ready in cash."
                        : "Your order is out for delivery and should reach you today.", null, false);
    }

    public Email delivered(OrderResponse o) {
        return order(o, "Order " + o.orderNumber() + " delivered", "Delivered",
                "Your order has been delivered. We hope you love it. Your invoice is available on the order page.", null, false);
    }

    public Email cancelled(OrderResponse o) {
        String refund = "REFUNDED".equals(o.paymentStatus())
                ? "Your payment of " + money(o.total()) + " is being refunded to the account you paid from. Banks usually show it within 5 to 7 working days."
                : null;
        return order(o, "Order " + o.orderNumber() + " cancelled", "Your order is cancelled",
                o.cancelReason() == null ? "Your order has been cancelled." : o.cancelReason() + ".", refund, false);
    }

    public Email refundStarted(OrderResponse o, BigDecimal amount) {
        return order(o, "Refund for order " + o.orderNumber(), "Your refund has started",
                "We have refunded " + money(amount) + " to the account you paid from. Banks usually show it within 5 to 7 working days.",
                null, false);
    }

    public Email newOrderForShop(OrderResponse o) {
        return order(o, "New order " + o.orderNumber() + " · " + money(o.total()), "New order",
                o.contact().name() + " placed an order (" + ("COD".equals(o.paymentMethod()) ? "Cash on Delivery" : "paid online") + ").",
                "Open it in the admin portal: " + siteUrl + "/admin/orders/" + o.orderNumber(), true);
    }

    public Email passwordReset(String name, String link, Instant expiresAt) {
        String body = p("Hi " + esc(firstName(name)) + ",")
                + p("We received a request to reset the password of your " + esc(shop) + " account. The link works once and expires at "
                        + esc(WHEN.format(expiresAt.atZone(INDIA))) + ".")
                + button(link, "Choose a new password")
                + p("If you did not ask for this, you can ignore this email; your password stays the same.");
        String text = "Hi " + firstName(name) + ",\n\nReset your " + shop + " password: " + link + "\nThe link expires at "
                + WHEN.format(expiresAt.atZone(INDIA)) + ".\n\nIf you did not ask for this, ignore this email.";
        return new Email("Reset your " + shop + " password", layout("Reset your password", body), text);
    }

    public Email contactForShop(String name, String email, String phone, String subject, String message) {
        String body = p("<strong>" + esc(name) + "</strong> &lt;" + esc(email) + "&gt;" + (phone == null ? "" : " · " + esc(phone)))
                + p("<strong>" + esc(subject) + "</strong>")
                + "<p style=\"white-space:pre-wrap;margin:0 0 16px\">" + esc(message) + "</p>"
                + button(siteUrl + "/admin/messages", "Open the inbox");
        return new Email("Contact form: " + subject, layout("New message", body),
                name + " <" + email + ">\n" + subject + "\n\n" + message);
    }

    // ------------------------------------------------------------------ building blocks

    private Email order(OrderResponse o, String subject, String heading, String intro, String extra, boolean withItems) {
        String link = siteUrl + "/order/" + o.orderNumber();
        StringBuilder body = new StringBuilder();
        body.append(p("Hi " + esc(firstName(o.contact().name())) + ","));
        body.append(p(esc(intro)));
        if (extra != null) body.append(p("<strong>" + esc(extra) + "</strong>"));
        StringBuilder text = new StringBuilder("Hi " + firstName(o.contact().name()) + ",\n\n" + intro + "\n");
        if (extra != null) text.append(extra).append('\n');
        text.append("\nOrder ").append(o.orderNumber()).append('\n');
        if (withItems) {
            body.append("<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;margin:8px 0 16px;font-size:14px\">");
            for (OrderResponse.Item i : o.items()) {
                body.append("<tr><td style=\"padding:6px 0;border-bottom:1px solid #eee\">").append(esc(i.name()))
                        .append("<br><span style=\"color:#777\">").append(esc(i.sizeCode())).append(" · ").append(esc(i.colorName()))
                        .append(" · Qty ").append(i.quantity()).append(i.designCode() == null ? "" : " · Custom design")
                        .append("</span></td><td style=\"padding:6px 0;border-bottom:1px solid #eee;text-align:right\">")
                        .append(money(i.lineTotal())).append("</td></tr>");
                text.append("- ").append(i.name()).append(" (").append(i.sizeCode()).append(", ").append(i.colorName()).append(") x")
                        .append(i.quantity()).append(": ").append(money(i.lineTotal())).append('\n');
            }
            row(body, "Shipping", o.shipping().signum() == 0 ? "Free" : money(o.shipping()));
            if (o.discount().signum() > 0) row(body, "Discount" + (o.couponCode() == null ? "" : " (" + o.couponCode() + ")"), "-" + money(o.discount()));
            body.append("<tr><td style=\"padding:8px 0;font-weight:700\">Total</td><td style=\"padding:8px 0;text-align:right;font-weight:700\">")
                    .append(money(o.total())).append("</td></tr></table>");
            OrderResponse.Address a = o.shippingAddress();
            body.append(p("<span style=\"color:#777\">Delivering to</span><br>" + esc(a.fullName()) + ", " + esc(a.line1())
                    + (a.line2() == null ? "" : ", " + esc(a.line2())) + ", " + esc(a.city()) + ", " + esc(a.state()) + " " + esc(a.pincode())));
            text.append("Total: ").append(money(o.total())).append('\n');
        }
        body.append(button(link, "View your order"));
        text.append("\nView your order: ").append(link).append('\n');
        return new Email(subject, layout(heading, body.toString()), text.toString());
    }

    private String layout(String heading, String body) {
        String help = supportEmail == null || supportEmail.isBlank() ? "" : " Questions? Write to " + esc(supportEmail) + ".";
        return "<!doctype html><html><body style=\"margin:0;background:#f4f2ee;font-family:Arial,Helvetica,sans-serif;color:#111\">"
                + "<div style=\"max-width:560px;margin:0 auto;padding:24px 16px\">"
                + "<div style=\"font-weight:700;letter-spacing:3px;font-size:14px;margin-bottom:16px\">" + esc(shop.toUpperCase(Locale.ROOT)) + "</div>"
                + "<div style=\"background:#fff;border-radius:12px;padding:24px\">"
                + "<h1 style=\"font-size:22px;margin:0 0 16px\">" + esc(heading) + "</h1>" + body + "</div>"
                + "<p style=\"font-size:12px;color:#777;margin:16px 4px\">This email was sent by " + esc(shop) + " about your account or order." + help
                + "</p></div></body></html>";
    }

    private static void row(StringBuilder body, String label, String value) {
        body.append("<tr><td style=\"padding:6px 0;color:#555\">").append(esc(label))
                .append("</td><td style=\"padding:6px 0;text-align:right\">").append(esc(value)).append("</td></tr>");
    }

    private static String p(String html) {
        return "<p style=\"margin:0 0 14px;line-height:1.5;font-size:15px\">" + html + "</p>";
    }

    private static String button(String url, String label) {
        return "<p style=\"margin:20px 0\"><a href=\"" + esc(url) + "\" style=\"background:#111;color:#fff;text-decoration:none;"
                + "padding:12px 20px;border-radius:999px;font-weight:700;display:inline-block\">" + esc(label) + "</a></p>";
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? "there" : name.trim().split("\\s+")[0];
    }

    static String money(BigDecimal amount) {
        return "₹" + com.shop.util.MoneyUtils.groupIndian(amount, false);
    }
}
