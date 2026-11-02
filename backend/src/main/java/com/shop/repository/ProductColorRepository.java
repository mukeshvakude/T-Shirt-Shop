package com.shop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shop.entity.ProductColor;

public interface ProductColorRepository extends JpaRepository<ProductColor, Long> {

    List<ProductColor> findAllByActiveTrueOrderBySortOrderAsc();
}
