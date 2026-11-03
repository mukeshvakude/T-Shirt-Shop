package com.shop.dto.order;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(@Size(max = 200) String reason) {
}
