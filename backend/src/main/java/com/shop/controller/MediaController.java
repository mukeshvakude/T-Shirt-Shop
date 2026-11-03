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
 * Serves stored images. Product and category images are public and cached everywhere.
 * Customer artwork and design previews live under random, unlisted URLs (capability links):
 * they are only handed to the owner and to admins, and are marked private and not indexable.
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "Stored images")
@SecurityRequirements
public class MediaController {

    private final StorageService storageService;

    @GetMapping("/{folder:products|categories|designs|previews}/{file}")
    @Operation(summary = "Get a stored image")
    public ResponseEntity<Resource> get(@PathVariable String folder, @PathVariable String file) {
        String key = folder + "/" + file;
        Resource resource = storageService.load(key)
                .orElseThrow(() -> new ResourceNotFoundException("Image", key));
        MediaType type = file.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        boolean customerFile = folder.equals("designs") || folder.equals("previews");
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff");
        if (customerFile) {
            response.cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate().immutable())
                    .header("X-Robots-Tag", "noindex, nofollow")
                    .header("Referrer-Policy", "no-referrer");
        } else {
            response.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        }
        return response.body(resource);
    }
}
