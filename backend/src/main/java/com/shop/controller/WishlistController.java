package com.shop.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.product.ProductSummaryResponse;
import com.shop.service.WishlistService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account/wishlist")
@RequiredArgsConstructor
@Tag(name = "Wishlist", description = "Products saved by the signed-in customer")
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    @Operation(summary = "Saved products, most recent first")
    public List<ProductSummaryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return wishlistService.list(userId(jwt));
    }

    @GetMapping("/ids")
    @Operation(summary = "Ids of saved products (to mark hearts in listings)")
    public List<Long> ids(@AuthenticationPrincipal Jwt jwt) {
        return wishlistService.productIds(userId(jwt));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Save a product (idempotent)")
    public ResponseEntity<Void> add(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        wishlistService.add(userId(jwt), productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Remove a saved product (idempotent)")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        wishlistService.remove(userId(jwt), productId);
        return ResponseEntity.noContent().build();
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
