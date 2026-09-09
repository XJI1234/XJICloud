package com.xjicloud.eci.credential;

import org.springframework.util.StringUtils;

/**
 * 根据 AK/SK 与 RAM 角色名解析建连模式。不读取 {@code ALIBABA_CLOUD_*} 环境变量。
 */
public final class AliyunCredentialResolver {

    private AliyunCredentialResolver() {
    }

    /**
     * 成对非空 AK/SK → {@link AliyunCredentialMode#STATIC_AK}（优先于角色）；
     * 否则有角色 → {@link AliyunCredentialMode#INSTANCE_PROFILE}；
     * 否则 {@link AliyunCredentialMode#DEFAULT_CHAIN}。
     */
    public static AliyunCredentialMode resolve(String accessKeyId, String accessKeySecret, String roleName) {
        if (StringUtils.hasText(accessKeyId) && StringUtils.hasText(accessKeySecret)) {
            return AliyunCredentialMode.STATIC_AK;
        }
        if (StringUtils.hasText(roleName)) {
            return AliyunCredentialMode.INSTANCE_PROFILE;
        }
        return AliyunCredentialMode.DEFAULT_CHAIN;
    }
}
