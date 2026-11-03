package com.shop.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.home.ContactRequest;
import com.shop.dto.home.NewsletterRequest;
import com.shop.dto.home.OfferResponse;
import com.shop.dto.home.ReviewHighlight;
import com.shop.security.ClientInfo;
import com.shop.service.EngagementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "Engagement", description = "Newsletter, contact form, offers and review highlights")
@SecurityRequirements
public class EngagementController {

    private final EngagementService engagementService;

    @PostMapping("/api/newsletter")
    @Operation(summary = "Subscribe an email address to the newsletter (always 202)")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody NewsletterRequest request, HttpServletRequest http) {
        engagementService.subscribe(request.email(), ClientInfo.from(http));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/contact")
    @Operation(summary = "Send a message to the shop (always 202)")
    public ResponseEntity<Void> contact(@Valid @RequestBody ContactRequest request,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        Long userId = jwt != null ? Long.valueOf(jwt.getSubject()) : null;
        engagementService.submitContact(request, ClientInfo.from(http), userId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/api/offers")
    @Operation(summary = "Coupons currently advertised by the shop")
    public List<OfferResponse> offers() {
        return engagementService.publicOffers();
    }

    @GetMapping("/api/reviews/highlights")
    @Operation(summary = "Recent approved 4 and 5 star reviews")
    public List<ReviewHighlight> reviewHighlights(@RequestParam(defaultValue = "6") @Min(1) @Max(24) int limit) {
        return engagementService.reviewHighlights(limit);
    }
}
