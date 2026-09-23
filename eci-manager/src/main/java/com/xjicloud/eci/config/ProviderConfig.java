package com.xjicloud.eci.config;

import com.xjicloud.eci.credential.AccessKeyPair;
import com.xjicloud.eci.credential.AliyunCredentialMode;
import com.xjicloud.eci.credential.AliyunCredentialResolver;
import com.xjicloud.eci.credential.AliyunResource;
import com.xjicloud.eci.credential.CredentialHint;
import com.xjicloud.eci.credential.RuntimeCredentialService;
import com.xjicloud.eci.provider.AliyunEciProvider;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.provider.MockEciProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 按 {@code eci.mock} 装配 Mock 或阿里云 Provider，并在启动时校验必填配置。
 */
@Configuration
public class ProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfig.class);

    /**
     * mock=true → {@link MockEciProvider}；否则校验云配置后返回 {@link AliyunEciProvider}。
     * {@code runtimeCredentials} 为 null 时跳过种子（单测），用 properties 当运行时钥。
     */
    @Bean
    public EciProvider eciProvider(EciProperties properties, RuntimeCredentialService runtimeCredentials) {
        if (runtimeCredentials != null) {
            runtimeCredentials.seedIfAbsent();
        }
        if (properties.isMock()) {
            log.info("ECI provider mode: MOCK autoCompleteSec={} status={}",
                    properties.getMockAutoCompleteSec(), properties.getMockAutoCompleteStatus());
            return new MockEciProvider(
                    properties.getMockAutoCompleteSec(),
                    properties.getMockAutoCompleteStatus());
        }
        AccessKeyPair runtimeKeys = runtimeCredentials != null
                ? runtimeCredentials.currentPair(AliyunResource.ECI_OPENAPI)
                : new AccessKeyPair(properties.getAccessKeyId(), properties.getAccessKeySecret());
        validateCloudConfig(properties);
        AliyunCredentialMode mode = AliyunCredentialResolver.resolve(
                runtimeKeys.accessKeyId(),
                runtimeKeys.accessKeySecret(),
                properties.getEcsRamRoleName());
        log.info("ECI provider mode: ALIYUN credentialMode={} akHint={} region={}",
                mode, CredentialHint.mask(runtimeKeys.accessKeyId()), properties.getRegionId());
        return new AliyunEciProvider(properties, runtimeKeys);
    }

    /** 单测入口：不经过种子。 */
    EciProvider eciProvider(EciProperties properties) {
        return eciProvider(properties, null);
    }

    /** 启动失败快：api-key 与轮询/超时配置合法性。 */
    @Bean
    public ApplicationRunner apiKeyStartupCheck(EciProperties properties) {
        return args -> {
            if (!StringUtils.hasText(properties.getApiKey())) {
                throw new IllegalStateException("eci.api-key 不能为空，请通过配置或环境变量 ECI_API_KEY 设置");
            }
            requirePositive(properties.getPollIntervalMs(), "eci.poll-interval-ms");
            requirePositive(properties.getTaskPollIntervalSec(), "eci.task-poll-interval-sec");
            requirePositive(properties.getCreateTimeoutSec(), "eci.create-timeout-sec");
            requirePositive(properties.getDeleteTimeoutSec(), "eci.delete-timeout-sec");
            requirePositive(properties.getDefaultMaxRuntimeSec(), "eci.default-max-runtime-sec");
            EciProperties.LifecycleAction lifecycleAction = properties.getLifecycleAction();
            requirePositive(lifecycleAction.getDispatchIntervalMs(),
                    "eci.lifecycle-action.dispatch-interval-ms");
            requirePositive(lifecycleAction.getBatchSize(), "eci.lifecycle-action.batch-size");
            requirePositive(lifecycleAction.getLeaseSec(), "eci.lifecycle-action.lease-sec");
            requirePositive(lifecycleAction.getRetryInitialSec(),
                    "eci.lifecycle-action.retry-initial-sec");
            requirePositive(lifecycleAction.getRetryMaxSec(), "eci.lifecycle-action.retry-max-sec");
            requirePositive(lifecycleAction.getCleanupAlertAttempts(),
                    "eci.lifecycle-action.cleanup-alert-attempts");
        };
    }

    /** 真实模式只校验网络/地域；云身份靠当前钥、ECS RAM 角色或默认凭证链，不强制 yml AK。 */
    private static void validateCloudConfig(EciProperties properties) {
        require(properties.getRegionId(), "eci.region-id");
        require(properties.getVswitchId(), "eci.vswitch-id");
        require(properties.getSecurityGroupId(), "eci.security-group-id");
    }

    private static void require(String value, String key) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(key + " 在 eci.mock=false 时必填");
        }
    }

    private static void requirePositive(long value, String key) {
        if (value <= 0) {
            throw new IllegalStateException(key + " 必须为正数，当前=" + value);
        }
    }
}
