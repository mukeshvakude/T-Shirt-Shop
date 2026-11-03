package com.shop.dto.design;

public record DesignAssetResponse(Long id, String url, String originalFilename, String mimeType, long sizeBytes,
        Integer width, Integer height) {
}
