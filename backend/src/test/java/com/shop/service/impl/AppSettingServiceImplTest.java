package com.shop.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shop.entity.AppSetting;
import com.shop.entity.SettingValueType;
import com.shop.repository.AppSettingRepository;

@ExtendWith(MockitoExtension.class)
class AppSettingServiceImplTest {

    @Mock
    private AppSettingRepository repository;

    @InjectMocks
    private AppSettingServiceImpl service;

    @Test
    void readsTypedValues() {
        stub("shipping.flat_charge", "49.00", SettingValueType.DECIMAL);
        stub("stock.reservation_minutes", " 15 ", SettingValueType.INT);
        stub("cod.enabled", "TRUE", SettingValueType.BOOLEAN);

        assertThat(service.getDecimal("shipping.flat_charge")).isEqualByComparingTo("49.00");
        assertThat(service.getInt("stock.reservation_minutes")).isEqualTo(15);
        assertThat(service.getBoolean("cod.enabled")).isTrue();
    }

    @Test
    void failsLoudlyForMissingSetting() {
        when(repository.findByKey("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getString("nope"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void failsLoudlyForMalformedValues() {
        stub("bad.int", "fifteen", SettingValueType.INT);
        stub("bad.bool", "yes", SettingValueType.BOOLEAN);
        stub("bad.decimal", "1,000", SettingValueType.DECIMAL);

        assertThatThrownBy(() -> service.getInt("bad.int")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.getBoolean("bad.bool")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.getDecimal("bad.decimal")).isInstanceOf(IllegalStateException.class);
    }

    private void stub(String key, String value, SettingValueType type) {
        AppSetting setting = new AppSetting();
        setting.setKey(key);
        setting.setValue(value);
        setting.setValueType(type);
        when(repository.findByKey(key)).thenReturn(Optional.of(setting));
    }
}
