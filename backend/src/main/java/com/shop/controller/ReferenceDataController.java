package com.shop.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.ColorResponse;
import com.shop.dto.SizeResponse;
import com.shop.entity.SizeGroup;
import com.shop.service.ReferenceDataService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
@Tag(name = "Reference data", description = "Sizes and colours used by filters and the designer")
@SecurityRequirements
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    @GetMapping("/sizes")
    @Operation(summary = "List active sizes, optionally filtered by group (APPAREL or WAIST)")
    public List<SizeResponse> sizes(@RequestParam(required = false) SizeGroup group) {
        return referenceDataService.getSizes(group);
    }

    @GetMapping("/colors")
    @Operation(summary = "List active colours")
    public List<ColorResponse> colors() {
        return referenceDataService.getColors();
    }
}
