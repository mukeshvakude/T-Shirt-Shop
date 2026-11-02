package com.shop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shop.entity.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {

    Optional<AppSetting> findByKey(String key);
}
