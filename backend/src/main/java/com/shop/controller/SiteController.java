package com.shop.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.config.WhatsAppProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/site")
@RequiredArgsConstructor
@Tag(name = "Site", description = "Public storefront settings kept on the server")
@SecurityRequirements
public class SiteController {

    /** {@code whatsappNumber}: digits with country code for wa.me links, or null when no chat number is set. */
    public record SiteResponse(String whatsappNumber) {
    }

    private final WhatsAppProperties whatsApp;

    @GetMapping
    @Operation(summary = "Storefront settings such as the WhatsApp chat number (short public cache)")
    public ResponseEntity<SiteResponse> site() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new SiteResponse(whatsApp.chatDigits()));
    }
}
