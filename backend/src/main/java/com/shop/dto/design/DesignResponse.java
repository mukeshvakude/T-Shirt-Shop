package com.shop.dto.design;

import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

/** A full design, enough to reopen it in the designer. */
public record DesignResponse(
        String code,
        String name,
        String tshirtType,
        String colorCode,
        String sizeCode,
        List<String> placements,
        JsonNode front,
        JsonNode back,
        JsonNode textItems,
        boolean premiumPrint,
        String previewFrontUrl,
        String previewBackUrl,
        List<DesignAssetResponse> assets,
        String status,
        boolean locked,
        String productSlug,
        Instant createdAt,
        Instant updatedAt) {
}
