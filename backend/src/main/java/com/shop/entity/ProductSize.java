package com.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_sizes")
public class ProductSize extends BaseEntity {

    @Column(nullable = false, length = 10, unique = true)
    private String code;

    @Column(nullable = false, length = 30)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "size_group", nullable = false, length = 20)
    private SizeGroup sizeGroup;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;
}
