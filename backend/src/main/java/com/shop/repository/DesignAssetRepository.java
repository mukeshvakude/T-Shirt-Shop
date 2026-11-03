package com.shop.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.DesignAsset;

public interface DesignAssetRepository extends JpaRepository<DesignAsset, Long> {

    List<DesignAsset> findByIdInAndUserId(Collection<Long> ids, Long userId);

    List<DesignAsset> findByCustomDesignId(Long customDesignId);

    long countByUserIdAndCreatedAtAfter(Long userId, java.time.Instant after);

    @Modifying
    @Query("update DesignAsset a set a.customDesignId = :designId where a.id in :ids and a.customDesignId is null")
    int attach(@Param("ids") Collection<Long> ids, @Param("designId") Long designId);

    @Modifying
    @Query("update DesignAsset a set a.customDesignId = null where a.customDesignId = :designId")
    int detach(@Param("designId") Long designId);
}
