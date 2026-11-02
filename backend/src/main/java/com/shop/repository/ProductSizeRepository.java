package com.shop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shop.entity.ProductSize;
import com.shop.entity.SizeGroup;

public interface ProductSizeRepository extends JpaRepository<ProductSize, Long> {

    List<ProductSize> findAllByActiveTrueOrderBySortOrderAsc();

    List<ProductSize> findAllBySizeGroupAndActiveTrueOrderBySortOrderAsc(SizeGroup sizeGroup);
}
