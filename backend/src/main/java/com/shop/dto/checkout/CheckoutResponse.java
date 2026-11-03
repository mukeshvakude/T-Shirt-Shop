package com.shop.dto.checkout;

import java.math.BigDecimal;
import java.util.List;

import com.shop.dto.account.AddressResponse;
import com.shop.dto.cart.CartResponse;

/**
 * Everything the checkout page needs: the validated cart, totals split the way the order stores
 * them (base prices and customization charges), the customer's contact details and saved
 * addresses, and which payment methods can be used for this order.
 */
public record CheckoutResponse(
        CartResponse cart,
        Totals totals,
        Contact contact,
        List<AddressResponse> addresses,
        List<PaymentOption> paymentOptions,
        int reservationMinutes) {

    public record Totals(BigDecimal itemsTotal, BigDecimal customizationTotal, BigDecimal shipping, BigDecimal discount,
            BigDecimal total) {
    }

    public record Contact(String name, String email, String phone) {
    }

    /** RAZORPAY or COD, with the reason when it cannot be used for this order. */
    public record PaymentOption(String method, boolean available, String message) {
    }
}
