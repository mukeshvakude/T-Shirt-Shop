package com.shop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shop.entity.CustomizationCharge;

public interface CustomizationChargeRepository extends JpaRepository<CustomizationCharge, Long> {

    List<CustomizationCharge> findAllByActiveTrue();

    Optional<CustomizationCharge> findByChargeTypeAndCode(String chargeType, String code);
}
