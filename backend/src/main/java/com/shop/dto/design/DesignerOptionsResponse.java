package com.shop.dto.design;

import java.math.BigDecimal;
import java.util.List;

/** Everything the designer needs to render choices, mockups and print areas. */
public record DesignerOptionsResponse(
        List<TshirtType> tshirtTypes,
        List<Choice> colors,
        List<Choice> sizes,
        List<Placement> placements,
        List<Font> fonts,
        List<Availability> availability,
        Limits limits) {

    public record TshirtType(String code, String label, String productSlug, BigDecimal basePrice, String mockup) {
    }

    /** {@code hex} is only set for colours. */
    public record Choice(String code, String name, String hex) {
    }

    /** Print area as fractions (0..1) of the mockup canvas. */
    public record Placement(String code, String label, String side, double x, double y, double w, double h,
            List<String> excludes) {
    }

    public record Font(String code, String label, String family) {
    }

    /** One blank T-Shirt variant: whether it can be ordered right now. */
    public record Availability(String type, String color, String size, boolean inStock) {
    }

    public record Limits(long maxUploadBytes, int maxDimensionPx, List<String> allowedTypes, int maxObjectsPerSide,
            int maxTextLength) {
    }
}
