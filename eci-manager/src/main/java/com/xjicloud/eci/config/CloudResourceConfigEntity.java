package com.xjicloud.eci.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 非密钥云资源配置覆盖值，表 {@code eci_cloud_config}；空字符串表示明确保存了空值。 */
@Entity
@Table(name = "eci_cloud_config")
public class CloudResourceConfigEntity {

    @Id
    @Column(name = "config_key", length = 128)
    private String configKey;

    @Column(name = "config_value", length = 2048, nullable = false)
    private String configValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CloudResourceConfigEntity() {
    }

    public CloudResourceConfigEntity(String configKey, String configValue, Instant updatedAt) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.updatedAt = updatedAt;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
