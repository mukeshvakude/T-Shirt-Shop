package com.shop.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.pricing.PriceEstimateRequest;
import com.shop.dto.pricing.PriceQuote;
import com.shop.dto.pricing.PricingInfo;
import com.shop.service.PricingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Pricing", description = "Live prices for custom T-Shirts, calculated on the server")
public class PricingController {

    private final PricingService pricing;

    @GetMapping("/charges")
    @Operation(summary = "Active customization charges and shipping rules")
    public ResponseEntity<PricingInfo> charges() {
        // Not cached: admin price changes must show up in the designer straight away.
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(pricing.info());
    }

    @PostMapping("/design-estimate")
    @Operation(summary = "Price breakdown for custom T-Shirt choices")
    public PriceQuote estimate(@Valid @RequestBody PriceEstimateRequest request) {
        return pricing.estimate(request);
    }
}
