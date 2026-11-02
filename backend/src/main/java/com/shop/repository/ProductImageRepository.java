package com.shop.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Query("""
            select i from ProductImage i left join fetch i.color
            where i.product.id in :productIds
            order by i.product.id, i.primaryImage desc, i.sortOrder, i.id""")
    List<ProductImage> findForProducts(@Param("productIds") Collection<Long> productIds);
}
