package com.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    public static final String CUSTOMER = "ROLE_CUSTOMER";
    public static final String ADMIN = "ROLE_ADMIN";

    @Column(nullable = false, length = 40, unique = true)
    private String name;

    @Column(length = 200)
    private String description;
}
