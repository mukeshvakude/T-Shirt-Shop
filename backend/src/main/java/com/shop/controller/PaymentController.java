package com.shop.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.order.OrderResponse;
import com.shop.dto.payment.PaymentFailedRequest;
import com.shop.dto.payment.PaymentStartRequest;
import com.shop.dto.payment.PaymentStartResponse;
import com.shop.dto.payment.VerifyPaymentRequest;
import com.shop.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Razorpay Standard Checkout: start or retry, verify, report failures")
public class PaymentController {

    private final PaymentService payments;

    @PostMapping("/create-order")
    @Operation(summary = "Start (or retry) online payment for one of my unpaid orders")
    public PaymentStartResponse start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PaymentStartRequest request) {
        return payments.start(Long.valueOf(jwt.getSubject()), request);
    }

    @PostMapping("/verify")
    @Operation(summary = "Confirm a payment with the Razorpay signature (checked on the server)")
    public OrderResponse verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VerifyPaymentRequest request) {
        return payments.verify(Long.valueOf(jwt.getSubject()), request);
    }

    @PostMapping("/failed")
    @Operation(summary = "Report a failed attempt from Razorpay Checkout")
    public OrderResponse failed(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PaymentFailedRequest request) {
        return payments.failed(Long.valueOf(jwt.getSubject()), request);
    }
}
