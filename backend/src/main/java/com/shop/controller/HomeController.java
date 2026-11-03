package com.shop.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.home.HomeResponse;
import com.shop.service.HomeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
@Tag(name = "Home", description = "Storefront home page content")
@SecurityRequirements
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    @Operation(summary = "All home page sections in one response (short public cache)")
    public ResponseEntity<HomeResponse> home() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(homeService.getHome());
    }
}
