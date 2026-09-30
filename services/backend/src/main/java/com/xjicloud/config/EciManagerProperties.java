package com.xjicloud.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xjicloud.eci-manager")
public record EciManagerProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        String trainingImage
) {
    public EciManagerProperties(String baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {
        this(baseUrl, apiKey, connectTimeout, readTimeout, "");
    }

    @ConstructorBinding
    public EciManagerProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://127.0.0.1:8081";
        }
        if (apiKey == null) {
            apiKey = "";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(20);
        }
        if (trainingImage == null) {
            trainingImage = "";
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isBlank();
    }
}
