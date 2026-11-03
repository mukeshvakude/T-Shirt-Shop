package com.shop.service;

import com.shop.dto.order.OrderResponse;
import com.shop.entity.Order;

/** Reading orders (full order management follows in Phase 15). */
public interface OrderService {

    /** One of the customer's own orders; 404 for anyone else's. */
    OrderResponse get(Long userId, String orderNumber);

    OrderResponse toResponse(Order order);
}
