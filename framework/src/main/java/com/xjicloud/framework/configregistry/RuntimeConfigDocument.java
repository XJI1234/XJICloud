package com.xjicloud.framework.configregistry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeConfigDocument(
        DatabaseConfig database,
        RedisConfig redis,
        OssConfig oss,
        StorageConfig storage,
        JwtConfig jwt,
        CorsConfig cors,
        WorkerConfig worker,
        BackendConfig backend,
        AliyunConfig aliyun
) {
    public RuntimeConfigDocument withSecretsMasked() {
        return new RuntimeConfigDocument(
                database != null ? database.withMaskedPassword() : null,
                redis != null ? redis.withMaskedPassword() : null,
                oss != null ? oss.withMaskedSecret() : null,
                storage,
                jwt != null ? jwt.withMaskedSecret() : null,
                cors,
                worker != null ? worker.withMaskedSecret() : null,
                backend,
                aliyun != null ? aliyun.withMaskedSecret() : null
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DatabaseConfig(String url, String username, String password) {
        public DatabaseConfig withMaskedPassword() {
            return new DatabaseConfig(url, username, SecretMask.mask(password));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RedisConfig(String host, Integer port, String password, String queueKey) {
        public RedisConfig withMaskedPassword() {
            return new RedisConfig(host, port, SecretMask.mask(password), queueKey);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OssConfig(
            String endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey,
            Boolean pathStyleAccess,
            Integer presignExpirationMinutes
    ) {
        public OssConfig withMaskedSecret() {
            return new OssConfig(endpoint, region, bucket, SecretMask.mask(accessKey), SecretMask.mask(secretKey), pathStyleAccess, presignExpirationMinutes);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StorageConfig(String root) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JwtConfig(String secret, Long expirationMs) {
        public JwtConfig withMaskedSecret() {
            return new JwtConfig(SecretMask.mask(secret), expirationMs);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CorsConfig(String allowedOrigins) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkerConfig(String sharedSecret, Integer heartbeatTimeoutSec, Integer pollTimeoutSec) {
        public WorkerConfig withMaskedSecret() {
            return new WorkerConfig(SecretMask.mask(sharedSecret), heartbeatTimeoutSec, pollTimeoutSec);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BackendConfig(String publicUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AliyunConfig(
            String accessKeyId,
            String accessKeySecret,
            String regionId,
            String vpcId,
            String vSwitchId,
            String securityGroupId,
            String containerImage
    ) {
        public AliyunConfig withMaskedSecret() {
            return new AliyunConfig(
                    SecretMask.mask(accessKeyId),
                    SecretMask.mask(accessKeySecret),
                    regionId, vpcId, vSwitchId, securityGroupId, containerImage
            );
        }
    }
}
