package com.shop.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.checkout.CheckoutResponse;
import com.shop.dto.checkout.PlaceOrderRequest;
import com.shop.dto.order.OrderResponse;
import com.shop.service.CheckoutService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
@Tag(name = "Checkout", description = "Order summary and placing orders (totals always calculated on the server)")
public class CheckoutController {

    private final CheckoutService checkout;

    @GetMapping
    @Operation(summary = "Validated cart, totals, contact details, saved addresses and payment options")
    public CheckoutResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return checkout.summary(Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/place-order")
    @Operation(summary = "Create the order from the cart: COD is confirmed, online orders wait for payment")
    public ResponseEntity<OrderResponse> place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checkout.placeOrder(Long.valueOf(jwt.getSubject()), request));
    }
}
