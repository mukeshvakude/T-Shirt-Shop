package com.shop.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.dto.PageResponse;
import com.shop.dto.order.AdminOrderStatusRequest;
import com.shop.dto.order.OrderResponse;
import com.shop.dto.order.OrderSummary;
import com.shop.dto.pricing.PriceLine;
import com.shop.entity.Order;
import com.shop.entity.OrderItem;
import com.shop.entity.Payment;
import com.shop.entity.ProductColor;
import com.shop.exception.BadRequestException;
import com.shop.exception.BusinessRuleException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.repository.OrderRepository;
import com.shop.repository.OrderStatusHistoryRepository;
import com.shop.repository.PaymentRepository;
import com.shop.repository.ProductColorRepository;
import com.shop.service.AppSettingService;
import com.shop.service.OrderService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Order history and details, customer cancellation, admin status changes and invoices. Amounts
 * always come from the order snapshot taken at checkout.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    /** Allowed admin moves; anything else is refused. */
    private static final Map<Order.Status, Set<Order.Status>> NEXT = Map.of(
            Order.Status.PENDING, EnumSet.of(Order.Status.CANCELLED),
            Order.Status.CONFIRMED, EnumSet.of(Order.Status.PROCESSING, Order.Status.CANCELLED),
            Order.Status.PROCESSING, EnumSet.of(Order.Status.SHIPPED, Order.Status.CANCELLED),
            Order.Status.SHIPPED, EnumSet.of(Order.Status.OUT_FOR_DELIVERY, Order.Status.DELIVERED),
            Order.Status.OUT_FOR_DELIVERY, EnumSet.of(Order.Status.DELIVERED));

    /** Statuses an admin may move an order to next. */
    static Set<Order.Status> nextStatuses(Order.Status from) {
        return NEXT.getOrDefault(from, Set.of());
    }

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ProductColorRepository colors;
    private final PaymentRepository payments;
    private final OrderLifecycle lifecycle;
    private final PaymentProcessor processor;
    private final InvoicePdf invoicePdf;
    private final AppSettingService settings;
    private final JsonMapper json;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher events;

    // ------------------------------------------------------------------ reading

    @Override
    public PageResponse<OrderSummary> list(Long userId, int page, int size) {
        return PageResponse.of(orders.findByUserIdOrderByPlacedAtDescIdDesc(userId, PageRequest.of(page, size)), this::summary);
    }

    @Override
    public OrderResponse get(Long userId, String orderNumber) {
        return toResponse(owned(userId, orderNumber));
    }

    // ------------------------------------------------------------------ customer cancellation

    @Override
    @Transactional
    public OrderResponse cancel(Long userId, String orderNumber, String reason) {
        Order order = lock(owned(userId, orderNumber));
        if (!canCancel(order)) {
            throw new BusinessRuleException("ORDER_NOT_CANCELLABLE", order.getOrderStatus() == Order.Status.CANCELLED
                    ? "This order is already cancelled."
                    : "This order is already being prepared and can no longer be cancelled here. Please contact us.");
        }
        String why = reason == null || reason.isBlank() ? "Cancelled by you" : "Cancelled by you: " + reason.trim();
        cancelAndSettle(order, why, userId, "CUSTOMER");
        log.info("Order {} cancelled by the customer", order.getOrderNumber());
        return toResponse(order);
    }

    // ------------------------------------------------------------------ admin status changes

    @Override
    @Transactional
    public OrderResponse updateStatus(Long adminId, String orderNumber, AdminOrderStatusRequest r) {
        Instant now = clock.instant();
        Order order = lock(orders.findByNumberWithItems(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber)));
        Order.Status from = order.getOrderStatus();
        Order.Status to = Order.Status.valueOf(r.status());
        if (!NEXT.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessRuleException("INVALID_STATUS_CHANGE", "An order that is " + label(from) + " cannot be marked "
                    + label(to) + ".");
        }
        String note = r.note() == null || r.note().isBlank() ? null : r.note().trim();
        if (to == Order.Status.CANCELLED) {
            cancelAndSettle(order, note == null ? "Cancelled by the shop" : "Cancelled by the shop: " + note, adminId, "ADMIN");
            return toResponse(order);
        }
        if (to == Order.Status.SHIPPED) {
            if (blank(r.courierName()) || blank(r.trackingNumber())) {
                throw new BadRequestException("TRACKING_REQUIRED", "Enter the courier and the tracking number to mark the order shipped.");
            }
            order.setShippedAt(now);
        }
        if (!blank(r.courierName())) order.setCourierName(r.courierName().trim());
        if (!blank(r.trackingNumber())) order.setTrackingNumber(r.trackingNumber().trim());
        if (to == Order.Status.DELIVERED) {
            order.setDeliveredAt(now);
            if (order.getShippedAt() == null) order.setShippedAt(now);
            if (order.getPaymentMethod() == Order.PaymentMethod.COD && order.getPaymentStatus() != Order.PaymentStatus.SUCCESS) {
                recordCashCollected(order, adminId, now);
            }
        }
        order.setOrderStatus(to);
        lifecycle.record(order, from.name(), note != null ? note : switch (to) {
            case PROCESSING -> "Your order is being prepared";
            case SHIPPED -> "Shipped with " + order.getCourierName() + " (tracking " + order.getTrackingNumber() + ")";
            case OUT_FOR_DELIVERY -> "Out for delivery";
            case DELIVERED -> "Delivered";
            default -> null;
        }, adminId, now);
        if (to == Order.Status.SHIPPED || to == Order.Status.OUT_FOR_DELIVERY || to == Order.Status.DELIVERED) {
            events.publishEvent(com.shop.notification.OrderEvent.of(order.getOrderNumber(), com.shop.notification.OrderEvent.Type.valueOf(to.name())));
        }
        log.info("Order {}: {} -> {} by admin {}", order.getOrderNumber(), from, to, adminId);
        return toResponse(order);
    }

    // ------------------------------------------------------------------ invoices

    @Override
    public Invoice invoice(Long userId, boolean admin, String orderNumber) {
        Order order = admin ? orders.findByNumberWithItems(orderNumber).orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber))
                : owned(userId, orderNumber);
        if (!invoiceAvailable(order)) {
            throw new BusinessRuleException("INVOICE_NOT_AVAILABLE", "The invoice is available once the order is confirmed.");
        }
        Instant date = order.getConfirmedAt() != null ? order.getConfirmedAt() : order.getPlacedAt();
        return new Invoice(InvoicePdf.invoiceNumber(order.getOrderNumber()) + ".pdf", invoicePdf.render(toResponse(order), date));
    }

    // ------------------------------------------------------------------ mapping

    @Override
    public OrderResponse toResponse(Order o) {
        Map<String, String> colorNames = colors.findAll().stream()
                .collect(Collectors.toMap(ProductColor::getCode, ProductColor::getName, (a, b) -> a));
        List<OrderResponse.Item> items = new ArrayList<>();
        for (OrderItem i : o.getItems()) {
            JsonNode breakdown = i.getPriceBreakdown() == null ? null : json.readTree(i.getPriceBreakdown());
            List<PriceLine> charges = new ArrayList<>();
            if (breakdown != null) {
                for (JsonNode c : breakdown.path("charges")) {
                    charges.add(new PriceLine(c.path("type").asString(""), c.path("code").asString(""),
                            c.path("label").asString(""), c.path("amount").decimalValue()));
                }
            }
            items.add(new OrderResponse.Item(i.getProductName(), i.getSku(), i.getSizeCode(), i.getColorCode(),
                    colorNames.getOrDefault(i.getColorCode(), i.getColorCode()), i.getImageUrl(), i.getQuantity(),
                    i.getUnitBasePrice(), i.getUnitCustomizationCharge(), i.getLineTotal(),
                    i.getCustomDesign() == null ? null : i.getCustomDesign().getDesignCode(),
                    i.getCustomDesign() == null ? null : i.getCustomDesign().getName(), charges));
        }
        JsonNode a = json.readTree(o.getShippingAddress());
        OrderResponse.Address address = new OrderResponse.Address(text(a, "fullName"), text(a, "phone"), text(a, "line1"),
                text(a, "line2"), text(a, "landmark"), text(a, "city"), text(a, "state"), text(a, "pincode"));
        List<OrderResponse.HistoryEntry> entries = history.findByOrderIdOrderByCreatedAtAscIdAsc(o.getId()).stream()
                .map(h -> new OrderResponse.HistoryEntry(h.getToStatus(), h.getCreatedAt(), h.getNote()))
                .toList();
        return new OrderResponse(o.getOrderNumber(), o.getOrderStatus().name(), o.getPaymentStatus().name(),
                o.getPaymentMethod().name(), o.getPlacedAt(), o.getReservationExpiresAt(), awaitingPayment(o), items, o.getSubtotal(),
                o.getCustomizationTotal(), o.getShippingCharge(), o.getDiscountTotal(), o.getCouponCode(),
                o.getGrandTotal(), o.getCurrency(),
                new OrderResponse.Contact(o.getContactName(), o.getContactEmail(), o.getContactPhone(), o.isWhatsappUpdates()), address,
                o.getCustomerNote(), o.getCancelReason(), entries, o.getConfirmedAt(), o.getShippedAt(), o.getDeliveredAt(),
                o.getCancelledAt(), o.getCourierName(), o.getTrackingNumber(), canCancel(o), invoiceAvailable(o));
    }

    private OrderSummary summary(Order o) {
        List<String> images = o.getItems().stream().map(OrderItem::getImageUrl).filter(java.util.Objects::nonNull)
                .distinct().limit(3).toList();
        int count = o.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        String first = o.getItems().isEmpty() ? null : o.getItems().get(0).getProductName();
        return new OrderSummary(o.getOrderNumber(), o.getPlacedAt(), o.getOrderStatus().name(), o.getPaymentStatus().name(),
                o.getPaymentMethod().name(), o.getGrandTotal(), count, first, images, awaitingPayment(o));
    }

    // ------------------------------------------------------------------ rules

    /** Cancelling is possible before the status in {@code order.customer_cancel_until_status} (PROCESSING). */
    private boolean canCancel(Order o) {
        if (o.getOrderStatus() == Order.Status.CANCELLED) return false;
        Order.Status until = Order.Status.valueOf(settings.getString("order.customer_cancel_until_status"));
        return o.getOrderStatus().ordinal() < until.ordinal();
    }

    private static boolean invoiceAvailable(Order o) {
        return o.getOrderStatus() != Order.Status.PENDING && o.getOrderStatus() != Order.Status.CANCELLED;
    }

    private static boolean awaitingPayment(Order o) {
        return o.getOrderStatus() == Order.Status.PENDING && o.getPaymentStatus() != Order.PaymentStatus.SUCCESS
                && o.getPaymentMethod() == Order.PaymentMethod.RAZORPAY;
    }

    /** Cancels the order and settles the money: paid online orders are refunded in full, open attempts closed. */
    private void cancelAndSettle(Order order, String reason, Long by, String source) {
        Instant now = clock.instant();
        lifecycle.cancel(order, reason, by, now);
        List<Payment> attempts = payments.findByOrderIdOrderByAttemptNoAsc(order.getId());
        Payment paid = attempts.stream().filter(p -> p.getStatus() == Order.PaymentStatus.SUCCESS
                && "RAZORPAY".equals(p.getProvider())).findFirst().orElse(null);
        if (paid != null) {
            processor.refund(order, paid, reason, source, now);
        } else {
            order.setPaymentStatus(Order.PaymentStatus.CANCELLED);
        }
        attempts.stream()
                .filter(p -> p.getStatus() == Order.PaymentStatus.CREATED || p.getStatus() == Order.PaymentStatus.PENDING)
                .forEach(p -> p.setStatus(Order.PaymentStatus.CANCELLED));
    }

    /** Delivery of a Cash on Delivery order means the cash was collected. */
    private void recordCashCollected(Order order, Long adminId, Instant now) {
        Payment cod = new Payment();
        cod.setOrderId(order.getId());
        cod.setProvider("COD");
        cod.setAttemptNo(payments.findTopByOrderIdOrderByAttemptNoDesc(order.getId()).map(p -> p.getAttemptNo() + 1).orElse(1));
        cod.setAmount(order.getGrandTotal());
        cod.setCurrency(order.getCurrency());
        cod.setStatus(Order.PaymentStatus.SUCCESS);
        cod.setMethod("cod");
        cod.setPaidAt(now);
        payments.saveAndFlush(cod);
        processor.log(cod, "COD_COLLECTED", null, order.getGrandTotal(), "SUCCESS", "ADMIN",
                Map.of("collected_by_admin", adminId), now);
        order.setPaymentStatus(Order.PaymentStatus.SUCCESS);
    }

    private Order owned(Long userId, String orderNumber) {
        return orders.findByNumberWithItems(orderNumber)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
    }

    private Order lock(Order found) {
        return orders.findByIdForUpdate(found.getId()).orElseThrow();
    }

    private static String label(Order.Status s) {
        return s.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }
}
