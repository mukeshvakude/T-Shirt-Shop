package com.shop.service;

import java.math.BigDecimal;

/**
 * Typed access to the admin-editable app_settings table.
 * Missing or malformed settings fail loudly rather than silently using a default.
 */
public interface AppSettingService {

    String getString(String key);

    int getInt(String key);

    BigDecimal getDecimal(String key);

    boolean getBoolean(String key);
}
