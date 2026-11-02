package com.shop.mapper;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.shop.dto.ColorResponse;
import com.shop.dto.SizeResponse;
import com.shop.dto.admin.AdminProductListItem;
import com.shop.dto.admin.AdminProductResponse;
import com.shop.dto.admin.AdminVariantResponse;
import com.shop.dto.product.ColorSwatch;
import com.shop.dto.product.ProductDetailResponse;
import com.shop.dto.product.ProductImageResponse;
import com.shop.dto.product.ProductSummaryResponse;
import com.shop.dto.product.VariantAvailability;
import com.shop.entity.Inventory;
import com.shop.entity.Product;
import com.shop.entity.ProductColor;
import com.shop.entity.ProductImage;
import com.shop.entity.ProductSize;

import lombok.RequiredArgsConstructor;

/**
 * Builds catalog and admin views of products. Variants and images are passed in
 * (loaded in batches by the services) so mapping never triggers lazy loading.
 */
@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final ReferenceDataMapper referenceDataMapper;

    public ProductSummaryResponse toSummary(Product p, List<ProductImage> images, List<Inventory> activeVariants) {
        return new ProductSummaryResponse(p.getId(), p.getName(), p.getSlug(), p.getCategory().getCode(),
                p.getCategory().getSlug(), p.getCategory().getName(), p.getBasePrice(), p.getDiscountPrice(),
                p.getEffectivePrice(), p.getDiscountPercent(), primaryImageUrl(images), p.getAvgRating(),
                p.getReviewCount(), sizeCodes(activeVariants), swatches(activeVariants), anyInStock(activeVariants),
                p.isFeatured(), p.isNewArrival(), p.isCustomizable());
    }

    public ProductDetailResponse toDetail(Product p, List<ProductImage> images, List<Inventory> activeVariants,
            int maxOrderQuantity) {
        Map<Long, ProductSize> sizes = new LinkedHashMap<>();
        Map<Long, ProductColor> colors = new LinkedHashMap<>();
        activeVariants.stream()
                .sorted(Comparator.comparingInt((Inventory i) -> i.getSize().getSortOrder()))
                .forEach(i -> sizes.putIfAbsent(i.getSize().getId(), i.getSize()));
        activeVariants.stream()
                .sorted(Comparator.comparingInt((Inventory i) -> i.getColor().getSortOrder()))
                .forEach(i -> colors.putIfAbsent(i.getColor().getId(), i.getColor()));

        List<SizeResponse> sizeResponses = sizes.values().stream().map(referenceDataMapper::toResponse).toList();
        List<ColorResponse> colorResponses = colors.values().stream().map(referenceDataMapper::toResponse).toList();
        List<VariantAvailability> variants = activeVariants.stream()
                .map(i -> new VariantAvailability(i.getSize().getCode(), i.getColor().getCode(), i.getSku(),
                        Math.min(i.getAvailable(), maxOrderQuantity), i.getAvailable() > 0,
                        i.getAvailable() > 0 && i.isLowStock()))
                .toList();

        return new ProductDetailResponse(p.getId(), p.getName(), p.getSlug(),
                referenceDataMapper.toResponse(p.getCategory()), p.getShortDescription(), p.getDescription(),
                p.getBasePrice(), p.getDiscountPrice(), p.getEffectivePrice(), p.getDiscountPercent(),
                p.getMaterial(), p.getFit(), p.getCareInstructions(), p.getAvgRating(), p.getReviewCount(),
                p.isFeatured(), p.isNewArrival(), p.isCustomizable(), anyInStock(activeVariants),
                images.stream().map(this::toImage).toList(), sizeResponses, colorResponses, variants);
    }

    public AdminProductListItem toAdminListItem(Product p, List<ProductImage> images, List<Inventory> activeVariants) {
        int available = activeVariants.stream().mapToInt(Inventory::getAvailable).sum();
        boolean lowStock = activeVariants.stream().anyMatch(Inventory::isLowStock);
        return new AdminProductListItem(p.getId(), p.getName(), p.getSlug(), p.getCategory().getId(),
                p.getCategory().getName(), p.getBasePrice(), p.getDiscountPrice(), p.getStatus(), p.isFeatured(),
                p.isNewArrival(), primaryImageUrl(images), available, activeVariants.size(), lowStock,
                p.getSoldCount(), p.getUpdatedAt());
    }

    public AdminProductResponse toAdmin(Product p, List<ProductImage> images, List<Inventory> allVariants) {
        return new AdminProductResponse(p.getId(), p.getName(), p.getSlug(), p.getCategory().getId(),
                p.getCategory().getName(), p.getCategory().getCode(), p.getShortDescription(), p.getDescription(),
                p.getBasePrice(), p.getDiscountPrice(), p.getStatus(), p.isFeatured(), p.isNewArrival(),
                p.isCustomizable(), p.getMaterial(), p.getFit(), p.getCareInstructions(), p.getAvgRating(),
                p.getReviewCount(), p.getSoldCount(), images.stream().map(this::toImage).toList(),
                allVariants.stream().map(this::toAdminVariant).toList(), p.getCreatedAt(), p.getUpdatedAt());
    }

    public AdminVariantResponse toAdminVariant(Inventory i) {
        return new AdminVariantResponse(i.getId(), i.getSku(), i.getSize().getId(), i.getSize().getCode(),
                i.getColor().getId(), i.getColor().getCode(), i.getColor().getName(), i.getColor().getHex(),
                i.getQuantityOnHand(), i.getQuantityReserved(), i.getAvailable(), i.getLowStockThreshold(),
                i.isLowStock(), i.isActive(), i.getProduct().getId(), i.getProduct().getName());
    }

    public ProductImageResponse toImage(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getImageUrl(), image.getAltText(),
                image.getColor() != null ? image.getColor().getCode() : null, image.getSortOrder(),
                image.isPrimaryImage());
    }

    private static String primaryImageUrl(List<ProductImage> images) {
        return images.stream()
                .filter(ProductImage::isPrimaryImage)
                .findFirst()
                .or(() -> images.stream().findFirst())
                .map(ProductImage::getImageUrl)
                .orElse(null);
    }

    private static List<String> sizeCodes(List<Inventory> variants) {
        return variants.stream()
                .map(Inventory::getSize)
                .sorted(Comparator.comparingInt(ProductSize::getSortOrder))
                .map(ProductSize::getCode)
                .distinct()
                .toList();
    }

    private static List<ColorSwatch> swatches(List<Inventory> variants) {
        Map<String, ColorSwatch> byCode = new LinkedHashMap<>();
        variants.stream()
                .map(Inventory::getColor)
                .sorted(Comparator.comparingInt(ProductColor::getSortOrder))
                .forEach(c -> byCode.putIfAbsent(c.getCode(), new ColorSwatch(c.getCode(), c.getName(), c.getHex())));
        return List.copyOf(byCode.values());
    }

    private static boolean anyInStock(List<Inventory> variants) {
        return variants.stream().anyMatch(i -> i.getAvailable() > 0);
    }
}
