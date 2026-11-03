package com.shop.service;

import com.shop.dto.cart.AddCartItemRequest;
import com.shop.dto.cart.CartResponse;
import com.shop.dto.cart.GuestCartRequest;
import com.shop.dto.cart.UpdateCartItemRequest;

/** Shopping cart with server-side prices, stock checks and coupons. */
public interface CartService {

    CartResponse get(Long userId);

    CartResponse add(Long userId, AddCartItemRequest request);

    CartResponse update(Long userId, Long itemId, UpdateCartItemRequest request);

    CartResponse remove(Long userId, Long itemId);

    CartResponse clear(Long userId);

    /** Moves a guest bag into the customer's cart after sign-in (quantities are added and capped). */
    CartResponse merge(Long userId, GuestCartRequest request);

    CartResponse applyCoupon(Long userId, String code);

    CartResponse removeCoupon(Long userId);

    /** Prices a guest bag without storing anything (no coupons for guests). */
    CartResponse quote(GuestCartRequest request);
}
