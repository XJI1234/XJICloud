package com.xjicloud.eci.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

/**
 * 异步任务线程池：供创建后轮询、删除确认等生命周期任务使用。
 * 同时启用 {@code @Scheduled}，供任务终态轮询器使用。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * 命名为 {@code eciLifecycleExecutor}，与 {@code @Async("eciLifecycleExecutor")} 对应。
     */
    @Bean(name = "eciLifecycleExecutor")
    public ThreadPoolTaskExecutor eciLifecycleExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("eci-lifecycle-");
        executor.initialize();
        return executor;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
