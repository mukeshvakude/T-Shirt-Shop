package com.shop.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customer's T-Shirt design: chosen blank (type, colour, size), print placements,
 * Fabric.js canvas JSON per side, text summary and rendered previews.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "custom_designs")
public class CustomDesign extends BaseEntity {

    public enum Status { DRAFT, IN_CART, ORDERED, ARCHIVED }

    @Column(name = "design_code", nullable = false, length = 20, unique = true)
    private String designCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_product_id", nullable = false)
    private Product baseProduct;

    @Column(length = 100)
    private String name;

    @Column(name = "tshirt_type", nullable = false, length = 40)
    private String tshirtType;

    @Column(name = "color_code", nullable = false, length = 20)
    private String colorCode;

    @Column(name = "size_code", nullable = false, length = 10)
    private String sizeCode;

    /** JSON array of placement codes, e.g. ["FRONT","BACK"]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String placements;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canvas_front", columnDefinition = "json")
    private String canvasFront;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canvas_back", columnDefinition = "json")
    private String canvasBack;

    /** JSON array of {side, text, font, size, color} for printing and pricing. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "text_items", columnDefinition = "json")
    private String textItems;

    @Column(name = "premium_design", nullable = false)
    private boolean premiumDesign;

    @Column(name = "preview_front_key", length = 255)
    private String previewFrontKey;

    @Column(name = "preview_back_key", length = 255)
    private String previewBackKey;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    /** Set once the design is ordered; it can then no longer be edited or deleted. */
    @Column(nullable = false)
    private boolean locked;

    @Version
    @Column(nullable = false)
    private Long version;
}
