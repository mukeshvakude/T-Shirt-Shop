package com.shop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.Address;

public interface AddressRepository extends JpaRepository<Address, Long> {

    /** Default first, then most recently updated. */
    @Query("select a from Address a where a.userId = :userId order by a.defaultAddress desc, a.updatedAt desc, a.id desc")
    List<Address> findForUser(@Param("userId") Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Address a set a.defaultAddress = false where a.userId = :userId and a.id <> :keepId and a.defaultAddress = true")
    int clearOtherDefaults(@Param("userId") Long userId, @Param("keepId") Long keepId);
}
