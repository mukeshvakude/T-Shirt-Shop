package com.shop.controller.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.pricing.AdminChargeResponse;
import com.shop.dto.pricing.ChargeUpdateRequest;
import com.shop.dto.pricing.ShippingSettings;
import com.shop.service.PricingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/pricing")
@RequiredArgsConstructor
@Tag(name = "Admin: pricing", description = "Customization charges and shipping (ROLE_ADMIN)")
public class AdminPricingController {

    private final PricingService pricing;

    @GetMapping("/charges")
    @Operation(summary = "Every chargeable choice with its current charge")
    public List<AdminChargeResponse> charges() {
        return pricing.adminCharges();
    }

    @PutMapping("/charges")
    @Operation(summary = "Set the charge for one choice (creates it when missing)")
    public AdminChargeResponse save(@Valid @RequestBody ChargeUpdateRequest request) {
        return pricing.saveCharge(request);
    }

    @GetMapping("/shipping")
    @Operation(summary = "Flat shipping charge and free-shipping threshold")
    public ShippingSettings shipping() {
        return pricing.shipping();
    }

    @PutMapping("/shipping")
    @Operation(summary = "Change the shipping charge and free-shipping threshold")
    public ShippingSettings updateShipping(@Valid @RequestBody ShippingSettings request) {
        return pricing.updateShipping(request);
    }
}
