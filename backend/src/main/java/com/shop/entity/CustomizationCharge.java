package com.shop.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Admin-editable extra charge for a custom T-Shirt choice. A missing row means no charge. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customization_charges")
public class CustomizationCharge extends BaseEntity {

    public static final String TSHIRT_TYPE = "TSHIRT_TYPE";
    public static final String PRINT_PLACEMENT = "PRINT_PLACEMENT";
    public static final String TEXT = "TEXT";
    public static final String DESIGN = "DESIGN";
    public static final String SIZE = "SIZE";
    public static final String COLOR = "COLOR";

    public static final String CUSTOM_TEXT = "CUSTOM_TEXT";
    public static final String PREMIUM_DESIGN = "PREMIUM_DESIGN";

    @Column(name = "option_id")
    private Long optionId;

    @Column(name = "charge_type", nullable = false, length = 30)
    private String chargeType;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;
}
