package com.xjicloud.eci.config;

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
     */
    @Bean
    public EciProvider eciProvider(EciProperties properties) {
        if (properties.isMock()) {
            log.info("ECI provider mode: MOCK");
            return new MockEciProvider();
        }
        validateCloudConfig(properties);
        String credentialHint = StringUtils.hasText(properties.getEcsRamRoleName())
                ? "INSTANCE_PROFILE role=" + properties.getEcsRamRoleName()
                : "DEFAULT_CHAIN";
        log.info("ECI provider mode: ALIYUN credential={} region={}",
                credentialHint, properties.getRegionId());
        return new AliyunEciProvider(properties);
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
        };
    }

    /** 真实模式只校验网络/地域；云身份靠 ECS RAM 角色或默认凭证链，不强制 yml AK。 */
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
