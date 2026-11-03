package com.shop.dto.design;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;

/**
 * Save or update a design. Canvases are Fabric.js JSON (validated on the server);
 * previews are PNG data URLs rendered by the browser.
 */
public record DesignRequest(
        @Size(max = 100) String name,
        @NotBlank @Size(max = 40) String tshirtType,
        @NotBlank @Size(max = 20) String colorCode,
        @NotBlank @Size(max = 10) String sizeCode,
        @NotNull @Size(min = 1, max = 2) List<@NotBlank @Size(max = 40) String> placements,
        JsonNode front,
        JsonNode back,
        @NotNull @Size(max = 20) List<@Valid TextItem> textItems,
        @NotNull @Size(max = 20) List<@NotNull Long> assetIds,
        boolean premiumPrint,
        @Size(max = 4_500_000) String previewFront,
        @Size(max = 4_500_000) String previewBack) {

    public record TextItem(
            @NotBlank @Pattern(regexp = "FRONT|BACK") String side,
            @NotBlank @Size(max = 100) String text,
            @NotBlank @Size(max = 40) String font,
            @Min(8) @Max(300) int size,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color) {
    }
}
