package com.shop.service;

import com.shop.dto.PageResponse;
import com.shop.dto.order.AdminOrderStatusRequest;
import com.shop.dto.order.OrderResponse;
import com.shop.dto.order.OrderSummary;
import com.shop.entity.Order;

/** Orders: history, details, tracking, cancellation, status changes and invoices. */
public interface OrderService {

    PageResponse<OrderSummary> list(Long userId, int page, int size);

    /** One of the customer's own orders; 404 for anyone else's. */
    OrderResponse get(Long userId, String orderNumber);

    OrderResponse toResponse(Order order);

    /** Customer cancellation before the order is being prepared; paid online orders are refunded. */
    OrderResponse cancel(Long userId, String orderNumber, String reason);

    /** Admin status change (Phase 16 adds the admin screens). */
    OrderResponse updateStatus(Long adminId, String orderNumber, AdminOrderStatusRequest request);

    Invoice invoice(Long userId, boolean admin, String orderNumber);

    record Invoice(String fileName, byte[] pdf) {
    }
}
