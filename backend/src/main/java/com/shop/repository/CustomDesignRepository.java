package com.shop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.CustomDesign;

public interface CustomDesignRepository extends JpaRepository<CustomDesign, Long> {

    @Query("select d from CustomDesign d join fetch d.baseProduct where d.designCode = :code")
    Optional<CustomDesign> findByCode(@Param("code") String code);

    @Query("select d from CustomDesign d join fetch d.baseProduct where d.userId = :userId order by d.updatedAt desc, d.id desc")
    List<CustomDesign> findForUser(@Param("userId") Long userId);

    long countByUserId(Long userId);

    boolean existsByDesignCode(String designCode);

    @Query(value = "select d from CustomDesign d join fetch d.baseProduct order by d.createdAt desc, d.id desc",
            countQuery = "select count(d) from CustomDesign d")
    Page<CustomDesign> findAllNewestFirst(Pageable pageable);
}
