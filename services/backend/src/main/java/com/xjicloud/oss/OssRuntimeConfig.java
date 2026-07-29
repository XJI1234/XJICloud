package com.xjicloud.oss;

public record OssRuntimeConfig(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess,
        int presignExpirationMinutes
) {
}
