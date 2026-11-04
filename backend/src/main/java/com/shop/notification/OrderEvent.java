package com.shop.notification;

import java.math.BigDecimal;

/** Something happened to an order that the customer should hear about (sent after the transaction commits). */
public record OrderEvent(String orderNumber, Type type, BigDecimal amount) {

    public enum Type { CONFIRMED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, REFUND_STARTED }

    public static OrderEvent of(String orderNumber, Type type) {
        return new OrderEvent(orderNumber, type, null);
    }
}
