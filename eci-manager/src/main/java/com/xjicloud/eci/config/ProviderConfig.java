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
        log.info("ECI provider mode: ALIYUN region={}", properties.getRegionId());
        return new AliyunEciProvider(properties);
    }

    /** 启动失败快：api-key 未配置则直接拒绝启动。 */
    @Bean
    public ApplicationRunner apiKeyStartupCheck(EciProperties properties) {
        return args -> {
            if (!StringUtils.hasText(properties.getApiKey())) {
                throw new IllegalStateException("eci.api-key 不能为空，请通过配置或环境变量 ECI_API_KEY 设置");
            }
        };
    }

    private static void validateCloudConfig(EciProperties properties) {
        require(properties.getAccessKeyId(), "eci.access-key-id");
        require(properties.getAccessKeySecret(), "eci.access-key-secret");
        require(properties.getRegionId(), "eci.region-id");
        require(properties.getVswitchId(), "eci.vswitch-id");
        require(properties.getSecurityGroupId(), "eci.security-group-id");
    }

    private static void require(String value, String key) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(key + " 在 eci.mock=false 时必填");
        }
    }
}
