package com.shop.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 100) @Pattern(regexp = Slugs.PATTERN, message = Slugs.MESSAGE) String slug,
        @Size(max = 500) String description,
        @Size(max = 500) String imageUrl,
        @Min(0) @Max(999) int sortOrder,
        boolean active) {
}
