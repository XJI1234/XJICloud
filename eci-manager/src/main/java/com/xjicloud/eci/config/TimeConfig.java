package com.xjicloud.eci.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 可注入的 UTC 时钟，供 HTTP Api-Key 重叠窗口与单测替换。
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
