package com.shop.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** T-Shirt types, print placements and fonts for the designer (seeded by Flyway V2). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customization_options")
public class CustomizationOption extends BaseEntity {

    public static final String TSHIRT_TYPE = "TSHIRT_TYPE";
    public static final String PRINT_PLACEMENT = "PRINT_PLACEMENT";
    public static final String FONT = "FONT";

    @Column(name = "option_type", nullable = false, length = 30)
    private String optionType;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String label;

    /** Raw JSON (print area box, mockup name, font family ...). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String metadata;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;
}
