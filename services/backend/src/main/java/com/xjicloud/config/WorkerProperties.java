package com.xjicloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xjicloud.worker")
public record WorkerProperties(
        String sharedSecret,
        int heartbeatTimeoutSec,
        int pollTimeoutSec
) {
}
