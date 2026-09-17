package com.xjicloud.eci.config.dto;

/**
 * {@code GET /api/v1/eci/meta} 的 data。不含 endpoint/bucket/密钥。
 */
public record ConsoleMetaResponse(
        boolean mock,
        String regionId,
        String defaultImage,
        double defaultCpu,
        double defaultMemory,
        String defaultShmSize,
        String defaultRestartPolicy,
        int defaultMaxRuntimeSec,
        boolean ossEnabled,
        boolean ossConfigured,
        int taskPollIntervalSec
) {
}
