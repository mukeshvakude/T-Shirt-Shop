package com.shop.service;

import com.shop.dto.order.OrderResponse;
import com.shop.dto.payment.PaymentFailedRequest;
import com.shop.dto.payment.PaymentStartRequest;
import com.shop.dto.payment.PaymentStartResponse;
import com.shop.dto.payment.VerifyPaymentRequest;

/** Online payments through Razorpay Standard Checkout. */
public interface PaymentService {

    /** Creates (or reuses) a Razorpay order for an unpaid online order; also used to retry. */
    PaymentStartResponse start(Long userId, PaymentStartRequest request);

    /** Checks the Razorpay signature and confirms the order. Safe to call twice. */
    OrderResponse verify(Long userId, VerifyPaymentRequest request);

    /** Records a failed attempt reported by the browser. */
    OrderResponse failed(Long userId, PaymentFailedRequest request);
}
