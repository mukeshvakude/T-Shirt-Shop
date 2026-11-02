package com.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Admin-editable key/value setting such as shipping charges. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_settings")
public class AppSetting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, length = 80, unique = true)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private SettingValueType valueType;

    @Column(length = 200)
    private String description;
}
