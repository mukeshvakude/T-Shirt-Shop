package com.shop.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.entity.AppSetting;
import com.shop.repository.AppSettingRepository;
import com.shop.service.AppSettingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppSettingServiceImpl implements AppSettingService {

    private final AppSettingRepository repository;

    @Override
    public String getString(String key) {
        return find(key).getValue();
    }

    @Override
    public int getInt(String key) {
        String raw = getString(key);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Setting " + key + " is not an integer: " + raw, ex);
        }
    }

    @Override
    public BigDecimal getDecimal(String key) {
        String raw = getString(key);
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Setting " + key + " is not a decimal: " + raw, ex);
        }
    }

    @Override
    public boolean getBoolean(String key) {
        String raw = getString(key).trim();
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        throw new IllegalStateException("Setting " + key + " is not a boolean: " + raw);
    }

    private AppSetting find(String key) {
        return repository.findByKey(key)
                .orElseThrow(() -> new IllegalStateException("Missing application setting: " + key));
    }
}
