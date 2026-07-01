package com.xjicloud.frameworkintegration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xjicloud.framework")
public record FrameworkIntegrationProperties(
        Boolean enabled,
        String masterUrl,
        String apiSecret,
        String backendApiSecret,
        int configPollIntervalSec
) {
    public FrameworkIntegrationProperties {
        if (enabled == null) {
            enabled = false;
        }
        if (configPollIntervalSec <= 0) {
            configPollIntervalSec = 60;
        }
    }
}
