package com.xjicloud.eci.config;

import java.util.Map;

/** 当前云资源配置的不可变快照。 */
public record CloudResourceConfigSnapshot(
        Network network,
        Oss oss,
        Advanced advanced,
        Map<String, ResolvedValue> values) {

    /** 网络分类运行时值。 */
    public record Network(String regionId, String vpcId, String vswitchId, String securityGroupId) {
    }

    /** OSS 分类运行时值。 */
    public record Oss(
            boolean enabled,
            String endpoint,
            String region,
            String bucket,
            boolean pathStyleAccess,
            int presignExpirationMinutes) {
    }

    /** 高级分类运行时值。 */
    public record Advanced(
            String defaultImage,
            String defaultShmSize,
            int createTimeoutSec,
            int failureLogTailLines,
            int failureLogGraceSec) {
    }

    /** 单字段最终文本值及来源（database / environment / default）。 */
    public record ResolvedValue(String value, String source) {
    }
}
