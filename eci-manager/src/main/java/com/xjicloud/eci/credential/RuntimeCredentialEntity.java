package com.xjicloud.eci.credential;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 运行时配置（当前云钥等），表 {@code eci_runtime_config}。
 */
@Entity
@Table(name = "eci_runtime_config")
public class RuntimeCredentialEntity {

    @Id
    @Column(name = "config_key", length = 128)
    private String configKey;

    @Column(name = "config_value", length = 2048)
    private String configValue;

    public RuntimeCredentialEntity() {
    }

    public RuntimeCredentialEntity(String configKey, String configValue) {
        this.configKey = configKey;
        this.configValue = configValue;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }
}
