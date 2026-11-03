package com.shop.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.Inventory;

import jakarta.persistence.LockModeType;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Query("""
            select i from Inventory i join fetch i.size s join fetch i.color c
            where i.product.id = :productId
            order by s.sortOrder, c.sortOrder""")
    List<Inventory> findAllForProduct(@Param("productId") Long productId);

    @Query("""
            select i from Inventory i join fetch i.size s join fetch i.color c
            where i.product.id in :productIds and i.active = true
            order by s.sortOrder, c.sortOrder""")
    List<Inventory> findActiveForProducts(@Param("productIds") Collection<Long> productIds);

    /** Row lock for stock changes, so concurrent orders and admin edits cannot oversell. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i join fetch i.product join fetch i.size join fetch i.color where i.id = :id")
    Optional<Inventory> findByIdForUpdate(@Param("id") Long id);

    @Query("select i from Inventory i join fetch i.product p join fetch p.category join fetch i.size join fetch i.color where i.sku = :sku")
    Optional<Inventory> findBySkuWithDetails(@Param("sku") String sku);

    boolean existsBySku(String sku);

    @Query(value = """
            select i from Inventory i join fetch i.product p join fetch i.size s join fetch i.color c
            where i.active = true and p.status <> com.shop.entity.ProductStatus.ARCHIVED
              and (i.quantityOnHand - i.quantityReserved) <= i.lowStockThreshold
            order by (i.quantityOnHand - i.quantityReserved) asc, p.name asc""",
            countQuery = """
            select count(i) from Inventory i join i.product p
            where i.active = true and p.status <> com.shop.entity.ProductStatus.ARCHIVED
              and (i.quantityOnHand - i.quantityReserved) <= i.lowStockThreshold""")
    Page<Inventory> findLowStock(Pageable pageable);

    @Query(value = "select count(*) from order_items where inventory_id = :id", nativeQuery = true)
    long countOrderItems(@Param("id") Long inventoryId);
}
