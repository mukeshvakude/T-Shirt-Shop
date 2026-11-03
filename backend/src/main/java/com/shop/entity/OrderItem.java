package com.shop.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One ordered line with name, SKU and price snapshots taken when the order was placed. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_design_id")
    private CustomDesign customDesign;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(name = "size_code", nullable = false, length = 10)
    private String sizeCode;

    @Column(name = "color_code", nullable = false, length = 20)
    private String colorCode;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitBasePrice;

    @Column(name = "unit_customization_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCustomizationCharge;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    /** Itemized charges and the design reference at order time. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "price_breakdown", columnDefinition = "json")
    private String priceBreakdown;
}
