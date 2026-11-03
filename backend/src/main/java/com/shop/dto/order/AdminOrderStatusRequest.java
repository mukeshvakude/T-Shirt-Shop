package com.shop.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Move an order forward (or cancel it). SHIPPED needs the courier and tracking number. */
public record AdminOrderStatusRequest(
        @NotBlank @Pattern(regexp = "PROCESSING|SHIPPED|OUT_FOR_DELIVERY|DELIVERED|CANCELLED") String status,
        @Size(max = 80) String courierName,
        @Size(max = 80) String trackingNumber,
        @Size(max = 200) String note) {
}
