package com.xjicloud.eci.config;

import com.xjicloud.eci.provider.AliyunEciProvider;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.provider.MockEciProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 启动配置校验：轮询/超时间隔必须为正数；Mock 装配透传自动终态配置。
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

    @Test
    void mockProviderReceivesAutoCompleteConfig() {
        EciProperties properties = validProperties();
        properties.setMock(true);
        properties.setMockAutoCompleteSec(5);
        properties.setMockAutoCompleteStatus("Failed");

        EciProvider provider = new ProviderConfig().eciProvider(properties);

        assertThat(provider).isInstanceOf(MockEciProvider.class);
        MockEciProvider mock = (MockEciProvider) provider;
        assertThat(mock.getMockAutoCompleteSec()).isEqualTo(5);
        assertThat(mock.getMockAutoCompleteStatus()).isEqualTo("Failed");
    }

    @Test
    void aliyunProviderRejectsMockFlag() {
        EciProperties properties = validProperties();
        properties.setMock(true);
        assertThatThrownBy(() -> new AliyunEciProvider(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eci.mock=true");
    }

    @Test
    void staticAkAssemblesAliyunProviderWithoutImds() {
        EciProperties properties = validProperties();
        properties.setMock(false);
        properties.setRegionId("cn-hangzhou");
        properties.setVswitchId("vsw-test");
        properties.setSecurityGroupId("sg-test");
        properties.setAccessKeyId("LTAItestkey01");
        properties.setAccessKeySecret("fake-secret-value");
        properties.setEcsRamRoleName("should-be-ignored");

        EciProvider provider = new ProviderConfig().eciProvider(properties);

        assertThat(provider).isInstanceOf(AliyunEciProvider.class);
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
