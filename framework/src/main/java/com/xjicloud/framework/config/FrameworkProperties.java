package com.xjicloud.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xjicloud.framework")
public record FrameworkProperties(
        String mode,
        int listenPort,
        String masterUrl,
        String agentToken,
        String advertiseHost,
        String dataDir,
        String backendUrl,
        String backendApiSecret,
        String apiSecret,
        String jwtSecret,
        String encryptionKey,
        Admin admin,
        Aliyun aliyun
) {
    public record Admin(String defaultUsername, String defaultPassword, boolean forcePasswordChange) {}
    public record Aliyun(
            String accessKeyId,
            String accessKeySecret,
            String regionId,
            String vpcId,
            String vSwitchId,
            String securityGroupId,
            String containerImage,
            boolean autoScaleEnabled,
            int scaleDownDelayMinutes
    ) {}

    public boolean isMaster() {
        return mode == null || "master".equalsIgnoreCase(mode);
    }

    public boolean isSlave() {
        return "slave".equalsIgnoreCase(mode);
    }
}
