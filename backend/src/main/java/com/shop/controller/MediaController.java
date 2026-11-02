package com.shop.controller;

import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.exception.ResourceNotFoundException;
import com.shop.service.StorageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Serves stored catalog images. Only product and category images are public;
 * customer design files are served by authenticated endpoints in Phase 9.
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "Public product and category images")
@SecurityRequirements
public class MediaController {

    private final StorageService storageService;

    @GetMapping("/{folder:products|categories}/{file}")
    @Operation(summary = "Get a stored product or category image")
    public ResponseEntity<Resource> get(@PathVariable String folder, @PathVariable String file) {
        String key = folder + "/" + file;
        Resource resource = storageService.load(key)
                .orElseThrow(() -> new ResourceNotFoundException("Image", key));
        MediaType type = file.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
