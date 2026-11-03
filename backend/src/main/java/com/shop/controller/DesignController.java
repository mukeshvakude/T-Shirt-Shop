package com.shop.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shop.dto.design.DesignAssetResponse;
import com.shop.dto.design.DesignRequest;
import com.shop.dto.design.DesignResponse;
import com.shop.dto.design.DesignSummary;
import com.shop.dto.design.DesignerOptionsResponse;
import com.shop.entity.Role;
import com.shop.service.DesignService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "Custom T-Shirt designer", description = "Designer options, artwork uploads and saved designs")
public class DesignController {

    private static final String CODE = "^DSN-[A-Z0-9]{8}$";

    private final DesignService designService;

    @GetMapping("/api/designer/options")
    @SecurityRequirements
    @Operation(summary = "T-Shirt types, colours, sizes, print areas, fonts, stock and upload limits")
    public ResponseEntity<DesignerOptionsResponse> options() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(60)).cachePublic())
                .body(designService.options());
    }

    @PostMapping(path = "/api/designs/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload artwork (PNG or JPG, checked by content)")
    public ResponseEntity<DesignAssetResponse> upload(@AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(designService.uploadAsset(userId(jwt), file.getBytes(), file.getOriginalFilename()));
    }

    @GetMapping("/api/designs")
    @Operation(summary = "My saved designs, most recent first")
    public List<DesignSummary> mine(@AuthenticationPrincipal Jwt jwt) {
        return designService.mine(userId(jwt));
    }

    @PostMapping("/api/designs")
    @Operation(summary = "Save a new design")
    public ResponseEntity<DesignResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DesignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(designService.create(userId(jwt), request));
    }

    @GetMapping("/api/designs/{code}")
    @Operation(summary = "Open a saved design (owner or admin)")
    public DesignResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = CODE) String code) {
        return designService.get(userId(jwt), isAdmin(jwt), code);
    }

    @PutMapping("/api/designs/{code}")
    @Operation(summary = "Update a design that is not part of an order")
    public DesignResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = CODE) String code,
            @Valid @RequestBody DesignRequest request) {
        return designService.update(userId(jwt), code, request);
    }

    @DeleteMapping("/api/designs/{code}")
    @Operation(summary = "Delete a design that is not part of an order")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = CODE) String code) {
        designService.delete(userId(jwt), code);
        return ResponseEntity.noContent().build();
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    private static boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(Role.ADMIN);
    }
}
