package com.shop.dto.cart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CouponRequest(@NotBlank @Size(max = 40) String code) {
}
