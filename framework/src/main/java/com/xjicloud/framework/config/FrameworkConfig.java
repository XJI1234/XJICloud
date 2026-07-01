package com.xjicloud.framework.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FrameworkProperties.class)
public class FrameworkConfig {
}
