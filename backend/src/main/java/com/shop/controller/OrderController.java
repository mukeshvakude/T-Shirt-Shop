package com.shop.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.order.OrderResponse;
import com.shop.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
@Tag(name = "Orders", description = "The customer's own orders")
public class OrderController {

    private final OrderService orders;

    @GetMapping("/{orderNumber}")
    @Operation(summary = "One of my orders")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = "^TS-[0-9]{8}-[0-9]{6}$") String orderNumber) {
        return orders.get(Long.valueOf(jwt.getSubject()), orderNumber);
    }
}
