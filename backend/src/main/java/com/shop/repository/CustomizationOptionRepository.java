package com.shop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shop.entity.CustomizationOption;

public interface CustomizationOptionRepository extends JpaRepository<CustomizationOption, Long> {

    List<CustomizationOption> findAllByOptionTypeAndActiveTrueOrderBySortOrderAsc(String optionType);
}
