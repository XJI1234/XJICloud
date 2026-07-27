package com.xjicloud.eci.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 启动配置校验：轮询/超时间隔必须为正数。
 */
class ProviderConfigValidationTest {

    @Test
    void rejectsNonPositiveTaskPollInterval() throws Exception {
        EciProperties properties = validProperties();
        properties.setTaskPollIntervalSec(0);
        ApplicationRunner runner = new ProviderConfig().apiKeyStartupCheck(properties);
        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eci.task-poll-interval-sec");
    }

    @Test
    void rejectsNonPositivePollIntervalMs() throws Exception {
        EciProperties properties = validProperties();
        properties.setPollIntervalMs(0);
        ApplicationRunner runner = new ProviderConfig().apiKeyStartupCheck(properties);
        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eci.poll-interval-ms");
    }

    @Test
    void acceptsPositiveIntervals() {
        ApplicationRunner runner = new ProviderConfig().apiKeyStartupCheck(validProperties());
        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
    }

    private static EciProperties validProperties() {
        EciProperties properties = new EciProperties();
        properties.setApiKey("test-key");
        properties.setPollIntervalMs(200);
        properties.setTaskPollIntervalSec(10);
        properties.setCreateTimeoutSec(300);
        properties.setDeleteTimeoutSec(300);
        properties.setDefaultMaxRuntimeSec(14400);
        return properties;
    }
}
