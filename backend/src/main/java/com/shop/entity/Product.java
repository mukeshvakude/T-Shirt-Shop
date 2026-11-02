package com.shop.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 170, unique = true)
    private String slug;

    @Column(name = "short_description", length = 300)
    private String shortDescription;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "discount_price", precision = 12, scale = 2)
    private BigDecimal discountPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "new_arrival", nullable = false)
    private boolean newArrival;

    @Column(name = "is_customizable", nullable = false)
    private boolean customizable;

    @Column(length = 100)
    private String material;

    @Column(length = 50)
    private String fit;

    @Column(name = "care_instructions", length = 300)
    private String careInstructions;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "sold_count", nullable = false)
    private int soldCount;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    private List<Inventory> variants = new ArrayList<>();

    /** The price the customer pays: discount price when set, otherwise base price. */
    public BigDecimal getEffectivePrice() {
        return discountPrice != null ? discountPrice : basePrice;
    }

    /** Whole-number discount percentage, 0 when not discounted. */
    public int getDiscountPercent() {
        if (discountPrice == null || basePrice == null || basePrice.signum() == 0) {
            return 0;
        }
        return basePrice.subtract(discountPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(basePrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
