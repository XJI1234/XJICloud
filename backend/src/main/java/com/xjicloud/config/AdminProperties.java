package com.xjicloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xjicloud.admin")
public record AdminProperties(
        String defaultUsername,
        String defaultPassword,
        boolean syncPasswordOnStartup
) {
    public AdminProperties {
        if (defaultUsername == null || defaultUsername.isBlank()) {
            defaultUsername = "admin";
        }
    }
}
