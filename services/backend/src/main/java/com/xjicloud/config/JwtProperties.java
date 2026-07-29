package com.xjicloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xjicloud.jwt")
public record JwtProperties(String secret, long expirationMs) {}
