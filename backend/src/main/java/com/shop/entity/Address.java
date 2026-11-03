package com.shop.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A saved delivery address. Each customer has at most one default address. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "addresses")
public class Address extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 30)
    private String label;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(nullable = false, length = 200)
    private String line1;

    @Column(length = 200)
    private String line2;

    @Column(length = 120)
    private String landmark;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 80)
    private String state;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 6)
    private String pincode;

    @Column(nullable = false, length = 60)
    private String country = "India";

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;
}
