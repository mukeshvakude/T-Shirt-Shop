package com.shop.dto.home;

import java.time.Instant;

/** A public review. The reviewer is shown as first name and last initial only, e.g. "Asha P.". */
public record ReviewHighlight(Long id, String reviewerName, int rating, String title, String comment,
        String productName, String productSlug, Instant createdAt) {
}
