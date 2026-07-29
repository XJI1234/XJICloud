package com.xjicloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xjicloud.redis")
public record RedisQueueProperties(String queueKey) {
}
