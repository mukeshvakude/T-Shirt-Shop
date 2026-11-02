package com.shop.controller.admin;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.PageResponse;
import com.shop.dto.admin.AdminVariantResponse;
import com.shop.dto.admin.StockUpdateRequest;
import com.shop.service.AdminProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin: inventory", description = "Stock levels per size and colour (ROLE_ADMIN)")
public class AdminInventoryController {

    private final AdminProductService service;

    @GetMapping("/low-stock")
    @Operation(summary = "Active SKUs at or below their low-stock threshold, lowest first")
    public PageResponse<AdminVariantResponse> lowStock(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return service.lowStock(page, size);
    }

    @PatchMapping("/{inventoryId}")
    @Operation(summary = "Set or adjust stock for one SKU")
    public AdminVariantResponse update(@PathVariable Long inventoryId, @Valid @RequestBody StockUpdateRequest request) {
        return service.updateStock(inventoryId, request);
    }
}
