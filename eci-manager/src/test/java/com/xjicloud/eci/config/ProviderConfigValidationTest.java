package com.xjicloud.eci.config;

import com.xjicloud.eci.credential.AliyunResource;
import com.xjicloud.eci.credential.RecordingRuntimeCredentialProbe;
import com.xjicloud.eci.credential.RuntimeCredentialEntity;
import com.xjicloud.eci.credential.RuntimeCredentialRepository;
import com.xjicloud.eci.credential.RuntimeCredentialService;
import com.xjicloud.eci.credential.RuntimeCredentialTester;
import com.xjicloud.eci.oss.OssStorageService;
import com.xjicloud.eci.provider.AliyunEciProvider;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.provider.MockEciProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void lifecycleActionDefaultsArePositiveAndCannotBeCleared() {
        EciProperties properties = new EciProperties();
        assertLifecycleActionDefaults(properties.getLifecycleAction());

        properties.setLifecycleAction(null);
        assertLifecycleActionDefaults(properties.getLifecycleAction());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dispatch-interval-ms",
            "batch-size",
            "lease-sec",
            "retry-initial-sec",
            "retry-max-sec",
            "cleanup-alert-attempts"
    })
    void rejectsNonPositiveLifecycleActionSettings(String setting) throws Exception {
        EciProperties properties = validProperties();
        EciProperties.LifecycleAction action = properties.getLifecycleAction();
        switch (setting) {
            case "dispatch-interval-ms" -> action.setDispatchIntervalMs(0);
            case "batch-size" -> action.setBatchSize(0);
            case "lease-sec" -> action.setLeaseSec(0);
            case "retry-initial-sec" -> action.setRetryInitialSec(0);
            case "retry-max-sec" -> action.setRetryMaxSec(0);
            case "cleanup-alert-attempts" -> action.setCleanupAlertAttempts(0);
            default -> throw new IllegalArgumentException(setting);
        }

        ApplicationRunner runner = new ProviderConfig().apiKeyStartupCheck(properties);
        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("eci.lifecycle-action." + setting);
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

    @Test
    void eciProviderDoesNotCopyRuntimeKeysOntoProperties() {
        EciProperties properties = validProperties();
        properties.setMock(false);
        properties.setRegionId("cn-hangzhou");
        properties.setVswitchId("vsw-test");
        properties.setSecurityGroupId("sg-test");
        properties.setAccessKeyId("LTAIseedeci01");
        properties.setAccessKeySecret("seed-secret-value");

        RuntimeCredentialRepository repository = mock(RuntimeCredentialRepository.class);
        when(repository.existsById(any())).thenReturn(true);
        when(repository.findById("current." + AliyunResource.ECI_OPENAPI.name() + ".access-key-id"))
                .thenReturn(Optional.of(new RuntimeCredentialEntity(
                        "current." + AliyunResource.ECI_OPENAPI.name() + ".access-key-id",
                        "LTAIputkey0001")));
        when(repository.findById("current." + AliyunResource.ECI_OPENAPI.name() + ".secret"))
                .thenReturn(Optional.of(new RuntimeCredentialEntity(
                        "current." + AliyunResource.ECI_OPENAPI.name() + ".secret",
                        "put-secret-value")));

        @SuppressWarnings("unchecked")
        ObjectProvider<EciProvider> eciProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OssStorageService> ossStorage = mock(ObjectProvider.class);
        RuntimeCredentialService service = new RuntimeCredentialService(
                repository, properties, new OssProperties(), eciProvider, ossStorage,
                new RuntimeCredentialTester(properties, new OssProperties(), new RecordingRuntimeCredentialProbe()));

        new ProviderConfig().eciProvider(properties, service);

        assertThat(properties.getAccessKeyId()).isEqualTo("LTAIseedeci01");
        assertThat(properties.getAccessKeySecret()).isEqualTo("seed-secret-value");
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

    private static void assertLifecycleActionDefaults(EciProperties.LifecycleAction action) {
        assertThat(action).isNotNull();
        assertThat(action.getDispatchIntervalMs()).isEqualTo(1000);
        assertThat(action.getBatchSize()).isEqualTo(20);
        assertThat(action.getLeaseSec()).isEqualTo(60);
        assertThat(action.getRetryInitialSec()).isEqualTo(2);
        assertThat(action.getRetryMaxSec()).isEqualTo(300);
        assertThat(action.getCleanupAlertAttempts()).isEqualTo(5);
    }
}
