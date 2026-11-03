package com.shop.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.cart.AddCartItemRequest;
import com.shop.dto.cart.CartResponse;
import com.shop.dto.cart.CouponRequest;
import com.shop.dto.cart.GuestCartRequest;
import com.shop.dto.cart.UpdateCartItemRequest;
import com.shop.service.CartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart priced on the server: products and custom designs, coupons, shipping")
public class CartController {

    private final CartService cart;

    @GetMapping
    @Operation(summary = "My cart with current prices, stock status and totals")
    public CartResponse get(@AuthenticationPrincipal Jwt jwt) {
        return cart.get(userId(jwt));
    }

    @PostMapping("/items")
    @Operation(summary = "Add a product variant (sku) or one of my designs (designCode, optional sizeCode)")
    public CartResponse add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddCartItemRequest request) {
        return cart.add(userId(jwt), request);
    }

    @PatchMapping("/items/{itemId}")
    @Operation(summary = "Change quantity, size or colour of a line")
    public CartResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cart.update(userId(jwt), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove a line")
    public CartResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId) {
        return cart.remove(userId(jwt), itemId);
    }

    @DeleteMapping
    @Operation(summary = "Empty the cart")
    public CartResponse clear(@AuthenticationPrincipal Jwt jwt) {
        return cart.clear(userId(jwt));
    }

    @PostMapping("/merge")
    @Operation(summary = "Move the guest bag from this browser into my cart")
    public CartResponse merge(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GuestCartRequest request) {
        return cart.merge(userId(jwt), request);
    }

    @PutMapping("/coupon")
    @Operation(summary = "Apply a coupon code")
    public CartResponse applyCoupon(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CouponRequest request) {
        return cart.applyCoupon(userId(jwt), request.code());
    }

    @DeleteMapping("/coupon")
    @Operation(summary = "Remove the coupon")
    public CartResponse removeCoupon(@AuthenticationPrincipal Jwt jwt) {
        return cart.removeCoupon(userId(jwt));
    }

    @PostMapping("/quote")
    @SecurityRequirements
    @Operation(summary = "Price a guest bag (SKUs and quantities) without signing in")
    public CartResponse quote(@Valid @RequestBody GuestCartRequest request) {
        return cart.quote(request);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
