package com.shop.dto.admin;

import com.shop.entity.ProductStatus;

import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(@NotNull ProductStatus status) {
}
