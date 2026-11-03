package com.shop.dto.design;

import java.time.Instant;
import java.util.List;

/** One row in "My designs" or the admin design list (customer fields are admin-only). */
public record DesignSummary(
        String code,
        String name,
        String tshirtType,
        String colorCode,
        String sizeCode,
        List<String> placements,
        boolean premiumPrint,
        String previewFrontUrl,
        String previewBackUrl,
        String status,
        boolean locked,
        Instant updatedAt,
        Long customerId,
        String customerName,
        String customerEmail) {
}
