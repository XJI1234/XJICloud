package com.xjicloud.eci.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务线程池：供创建后轮询、删除确认等生命周期任务使用。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 命名为 {@code eciLifecycleExecutor}，与 {@code @Async("eciLifecycleExecutor")} 对应。
     */
    @Bean(name = "eciLifecycleExecutor")
    public Executor eciLifecycleExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("eci-lifecycle-");
        executor.initialize();
        return executor;
    }
}
