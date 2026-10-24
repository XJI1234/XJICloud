package com.xjicloud.eci.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 绑定配置前缀 {@code eci.oss.} 的 OSS 属性。
 * <p>
 * 字段语义对齐主项目 {@code xjicloud.oss}；对接时填相同值即可共享 Bucket。
 * {@code enabled=false} 或缺少 endpoint/bucket 时不建热切客户端；普通任务仍可跑。
 * 带 {@code storage} 的创建还须活客户端已就绪。
 */
@ConfigurationProperties(prefix = "eci.oss")
public class OssProperties {

    /** 为 false 时拒绝带 storage 的请求；服务仍可正常启动。 */
    private boolean enabled = false;
    /** 必填（启用时）：OSS/S3 兼容 endpoint，勿带 path。 */
    private String endpoint = "";
    /** 可选；阿里云场景下 SDK 用 AWS_GLOBAL，此字段主要透传给容器 env。 */
    private String region = "";
    /** 必填（启用时）：目标 Bucket。 */
    private String bucket = "";
    /** 必填（启用时）：Access Key。 */
    private String accessKey = "";
    /** 必填（启用时）：Secret Key。 */
    private String secretKey = "";
    /** MinIO 等常开；阿里云由 SDK 侧强制 virtual-hosted，此值会被忽略。 */
    private boolean pathStyleAccess = false;
    /** 预签名 URL 有效期（分钟）。 */
    private int presignExpirationMinutes = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public int getPresignExpirationMinutes() {
        return presignExpirationMinutes;
    }

    public void setPresignExpirationMinutes(int presignExpirationMinutes) {
        this.presignExpirationMinutes = presignExpirationMinutes;
    }

    /**
     * 网络目标是否齐全（不含 AccessKey）。种子 AK 只用于 {@link #isConfigured()} / {@code init()}。
     */
    public boolean hasTarget() {
        return StringUtils.hasText(endpoint) && StringUtils.hasText(bucket);
    }

    /**
     * 判断凭证与目标是否齐全，可供 {@code OssStorageService.init()} 用 properties 建连。
     * <p>
     * 必填：{@code endpoint}、{@code bucket}、{@code accessKey}、{@code secretKey}（均非空白）。
     * 任务 storage 与热切客户端不走此判定，以免把种子 AK 当成当前钥。
     */
    public boolean isConfigured() {
        return hasTarget()
                && StringUtils.hasText(accessKey)
                && StringUtils.hasText(secretKey);
    }

    /** 将云资源配置快照投影到兼容字段；AK/SK 仍由运行时凭证表持有，不在这里替换。 */
    public void applyCloudConfig(CloudResourceConfigSnapshot snapshot) {
        CloudResourceConfigSnapshot.Oss oss = snapshot.oss();
        this.enabled = oss.enabled();
        this.endpoint = oss.endpoint();
        this.region = oss.region();
        this.bucket = oss.bucket();
        this.pathStyleAccess = oss.pathStyleAccess();
        this.presignExpirationMinutes = oss.presignExpirationMinutes();
    }
}
