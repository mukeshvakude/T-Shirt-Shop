package com.shop.service;

import com.shop.dto.checkout.CheckoutResponse;
import com.shop.dto.checkout.PlaceOrderRequest;
import com.shop.dto.order.OrderResponse;

/** Turns the validated cart into an order with server-calculated totals and a stock hold. */
public interface CheckoutService {

    CheckoutResponse summary(Long userId);

    OrderResponse placeOrder(Long userId, PlaceOrderRequest request);
}
