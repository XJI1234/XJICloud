package com.xjicloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xjicloud.storage")
public record StorageProperties(String root) {}
