package com.shop.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.shop.entity.CustomizationOption;
import com.shop.entity.Product;
import com.shop.entity.ProductStatus;
import com.shop.exception.BadRequestException;
import com.shop.exception.BusinessRuleException;
import com.shop.repository.CustomizationOptionRepository;
import com.shop.repository.InventoryRepository;
import com.shop.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared rules for custom T-Shirts, used by the designer and by pricing so both accept and
 * reject exactly the same choices: T-Shirt type, its base product, offered variants and
 * print placements (one per side, no excluded combinations).
 */
@Component
@RequiredArgsConstructor
public class DesignRules {

    private final CustomizationOptionRepository options;
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final JsonMapper json;

    public List<CustomizationOption> active(String type) {
        return options.findAllByOptionTypeAndActiveTrueOrderBySortOrderAsc(type);
    }

    public CustomizationOption type(String code) {
        return active(CustomizationOption.TSHIRT_TYPE).stream()
                .filter(o -> o.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("UNKNOWN_TSHIRT_TYPE", "Choose a T-Shirt type."));
    }

    /** The active, customizable blank product behind a T-Shirt type. */
    public Product baseProduct(CustomizationOption type) {
        String slug = text(metadata(type), "baseProductSlug");
        return (slug == null ? Optional.<Product>empty() : products.findVisibleBySlug(slug, ProductStatus.ACTIVE))
                .filter(Product::isCustomizable)
                .orElseThrow(() -> new BusinessRuleException("TSHIRT_TYPE_UNAVAILABLE",
                        "This T-Shirt type is not available right now."));
    }

    /** The colour and size must be offered for the type (stock is checked when ordering). */
    public void requireVariant(Product product, String colorCode, String sizeCode, String typeLabel) {
        boolean exists = inventory.findActiveForProducts(List.of(product.getId())).stream()
                .anyMatch(i -> i.getColor().getCode().equals(colorCode) && i.getSize().getCode().equals(sizeCode));
        if (!exists) {
            throw new BadRequestException("VARIANT_UNAVAILABLE",
                    "That colour and size combination is not offered for " + typeLabel + ".");
        }
    }

    /**
     * Validates placements and returns the sides they print on.
     *
     * @throws BadRequestException UNKNOWN_PLACEMENT or PLACEMENT_CONFLICT
     */
    public Set<String> checkPlacements(List<String> placements) {
        Map<String, CustomizationOption> known = active(CustomizationOption.PRINT_PLACEMENT).stream()
                .collect(Collectors.toMap(CustomizationOption::getCode, o -> o));
        Set<String> sides = new HashSet<>();
        for (String code : placements) {
            CustomizationOption option = known.get(code);
            if (option == null) {
                throw new BadRequestException("UNKNOWN_PLACEMENT", "Unknown print placement " + code + ".");
            }
            JsonNode meta = metadata(option);
            if (!sides.add(text(meta, "side"))) {
                throw new BadRequestException("PLACEMENT_CONFLICT", "Choose one print placement per side.");
            }
            for (JsonNode excluded : meta.path("excludes")) {
                if (placements.contains(excluded.stringValue())) {
                    throw new BadRequestException("PLACEMENT_CONFLICT",
                            option.getLabel() + " cannot be combined with " + excluded.stringValue() + ".");
                }
            }
        }
        return sides;
    }

    public JsonNode metadata(CustomizationOption option) {
        String raw = option.getMetadata();
        return raw == null ? json.createObjectNode() : json.readTree(raw);
    }

    public static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.stringValue() : null;
    }
}
