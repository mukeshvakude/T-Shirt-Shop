package com.shop.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.dto.order.OrderResponse;
import com.shop.dto.pricing.PriceLine;
import com.shop.entity.Order;
import com.shop.entity.OrderItem;
import com.shop.entity.ProductColor;
import com.shop.exception.ResourceNotFoundException;
import com.shop.repository.OrderRepository;
import com.shop.repository.OrderStatusHistoryRepository;
import com.shop.repository.ProductColorRepository;
import com.shop.service.OrderService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ProductColorRepository colors;
    private final JsonMapper json;

    @Override
    public OrderResponse get(Long userId, String orderNumber) {
        Order order = orders.findByNumberWithItems(orderNumber)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
        return toResponse(order);
    }

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
        boolean awaiting = o.getOrderStatus() == Order.Status.PENDING && o.getPaymentStatus() != Order.PaymentStatus.SUCCESS
                && o.getPaymentMethod() == Order.PaymentMethod.RAZORPAY;
        return new OrderResponse(o.getOrderNumber(), o.getOrderStatus().name(), o.getPaymentStatus().name(),
                o.getPaymentMethod().name(), o.getPlacedAt(), o.getReservationExpiresAt(), awaiting, items, o.getSubtotal(),
                o.getCustomizationTotal(), o.getShippingCharge(), o.getDiscountTotal(), o.getCouponCode(),
                o.getGrandTotal(), o.getCurrency(),
                new OrderResponse.Contact(o.getContactName(), o.getContactEmail(), o.getContactPhone()), address,
                o.getCustomerNote(), o.getCancelReason(), entries);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }
}
