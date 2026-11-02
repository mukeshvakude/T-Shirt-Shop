package com.shop.dto.auth;

import java.time.Instant;
import java.util.Set;

public record UserResponse(Long id, String email, String fullName, String phone, boolean emailVerified,
        Set<String> roles, Instant createdAt) {
}
