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

@Configuration
public class ProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfig.class);

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
