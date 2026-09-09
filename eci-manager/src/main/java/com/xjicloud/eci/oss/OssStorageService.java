package com.xjicloud.eci.oss;

import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.credential.AccessKeyPair;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 精简版 OSS 客户端：presign PUT/GET 与 key 构建。
 * <p>
 * 从主项目移植；支持 {@link #applyRuntimeKeys} 热切换当前 AK（endpoint/bucket 仍读 {@link OssProperties}）。
 * {@code enabled=false} 或凭证/目标不完整时 {@link #init()} 跳过建连，服务仍可启动。
 */
@Service
public class OssStorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageService.class);

    private final OssProperties properties;
    private volatile S3Client s3Client;
    private volatile S3Presigner s3Presigner;

    public OssStorageService(OssProperties properties) {
        this.properties = properties;
    }

    /**
     * 单测/显式建连：仅当 {@code enabled=true} 且 {@link OssProperties#isConfigured()} 时建客户端。
     * Spring 启动走 {@link com.xjicloud.eci.credential.RuntimeCredentialService#applyPersistedOssKeys()}。
     */
    public void init() {
        if (!properties.isEnabled() || !properties.isConfigured()) {
            log.info("OSS client skipped: enabled={}, configured={}",
                    properties.isEnabled(), properties.isConfigured());
            return;
        }
        rebuildClients(properties.getAccessKey(), properties.getSecretKey());
    }

    /**
     * 热切换当前 OSS AccessKey；endpoint/bucket 仍来自 {@link OssProperties}。
     * 未启用或 endpoint/bucket 不完整时只记日志、不抛错。
     */
    public void applyRuntimeKeys(String accessKeyId, String accessKeySecret) {
        if (!new AccessKeyPair(accessKeyId, accessKeySecret).isComplete()) {
            log.info("OSS applyRuntimeKeys skipped: access key pair incomplete");
            return;
        }
        if (!properties.isEnabled() || !properties.hasTarget()) {
            log.info("OSS applyRuntimeKeys skipped: enabled={}, endpoint/bucket incomplete",
                    properties.isEnabled());
            return;
        }
        rebuildClients(accessKeyId.trim(), accessKeySecret.trim());
    }

    private void rebuildClients(String accessKey, String secretKey) {
        String sdkEndpoint = normalizeEndpointForSdk(properties.getEndpoint());
        String sdkHost = extractHostFromEndpoint(sdkEndpoint);
        if (sdkHost == null || sdkHost.isBlank()) {
            throw new IllegalStateException("OSS endpoint 无效: " + properties.getEndpoint());
        }
        Region sdkRegion = resolveSdkRegion(sdkEndpoint, properties.getRegion());
        boolean pathStyleForSdk = resolvePathStyleForSdk(sdkEndpoint, properties.isPathStyleAccess());
        log.info("OSS S3 client: endpoint={}, region={}, bucket={}, pathStyle={}",
                sdkEndpoint, sdkRegion, properties.getBucket(), pathStyleForSdk);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleForSdk)
                .chunkedEncodingEnabled(false)
                .build();
        S3Client nextClient = S3Client.builder()
                .endpointOverride(URI.create(sdkEndpoint))
                .region(sdkRegion)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
        S3Presigner nextPresigner = S3Presigner.builder()
                .endpointOverride(URI.create(sdkEndpoint))
                .region(sdkRegion)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
        swapClients(nextClient, nextPresigner);
    }

    /**
     * 先赋新引用再关闭旧客户端。包可见以便测试 {@code close()}。
     */
    void swapClients(S3Client nextClient, S3Presigner nextPresigner) {
        S3Client previousClient = this.s3Client;
        S3Presigner previousPresigner = this.s3Presigner;
        this.s3Client = nextClient;
        this.s3Presigner = nextPresigner;
        closeQuietly(previousClient);
        closeQuietly(previousPresigner);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // 热切换时关闭旧客户端失败不阻断新客户端
        }
    }

    /**
     * 签发 PUT 预签名 URL；{@code contentType} 会写入签名请求，客户端上传必须带相同 Content-Type。
     */
    public String presignPutUrl(String ossKey, String contentType) {
        requireReady();
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(ossKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(properties.getPresignExpirationMinutes()))
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return presigned.url().toString();
    }

    public String presignGetUrl(String ossKey) {
        requireReady();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(properties.getPresignExpirationMinutes()))
                .getObjectRequest(builder -> builder.bucket(properties.getBucket()).key(ossKey))
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    public String buildDatasetPrefix(UUID jobId) {
        return "datasets/" + jobId + "/";
    }

    public String buildDatasetImageKey(UUID jobId, String archivedName) {
        return buildDatasetPrefix(jobId) + "images/" + archivedName;
    }

    /**
     * 主项目训练输出约定路径（固定 {@code model.ply}，与 backend 一致；格式变更属二期）。
     */
    public String buildOutputKey(UUID jobId) {
        return "outputs/" + jobId + "/model.ply";
    }

    /**
     * 探测 Bucket 可达性：对 {@link OssProperties#getBucket()} 执行 {@code ListObjectsV2 maxKeys=1}。
     * <ul>
     *   <li>客户端未初始化 → {@link IllegalStateException}（「OSS 未配置/未启用」）</li>
     *   <li>HTTP 403 → {@link IllegalStateException}（鉴权失败）</li>
     *   <li>网络/DNS 等 {@link SdkClientException} → {@link IllegalStateException}（连接失败，含请求主机提示）</li>
     *   <li>其他 {@link S3Exception} 原样抛出</li>
     * </ul>
     */
    public void testConnection() {
        requireReady();
        String sdkEndpoint = normalizeEndpointForSdk(properties.getEndpoint());
        boolean pathStyleForSdk = resolvePathStyleForSdk(sdkEndpoint, properties.isPathStyleAccess());
        try {
            s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(properties.getBucket())
                    .maxKeys(1)
                    .build());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 403) {
                throw new IllegalStateException(
                        "OSS 鉴权失败（403）。请确认 Access Key / Secret Key。详情: "
                                + ex.awsErrorDetails().errorMessage(),
                        ex
                );
            }
            throw ex;
        } catch (SdkClientException ex) {
            String hostHint = resolveRequestHost(properties.getBucket(), sdkEndpoint, pathStyleForSdk);
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException(
                    "OSS 连接失败，请检查 endpoint/密钥及服务器 DNS。请求主机: " + hostHint
                            + "，原因: " + cause.getMessage(),
                    ex
            );
        }
    }

    private void requireReady() {
        if (s3Presigner == null || s3Client == null) {
            throw new IllegalStateException("OSS 未配置/未启用");
        }
    }

    /**
     * 阿里云 OSS + AWS SDK：endpoint 用 oss-cn-{region}.aliyuncs.com（勿加 s3. 前缀）。
     * virtual-hosted 下 SDK 会拼成 {bucket}.oss-cn-{region}.aliyuncs.com；
     * 若 endpoint 为 s3.oss-...，SDK 会错误解析成 {bucket}.s3. 导致 DNS 失败。
     */
    static String normalizeEndpointForSdk(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return endpoint;
        }
        String trimmed = endpoint.trim();
        if (!trimmed.contains("aliyuncs.com")) {
            return trimmed;
        }
        String scheme = trimmed.contains("://") ? trimmed.substring(0, trimmed.indexOf("://")) : "https";
        String host = trimmed.contains("://") ? trimmed.substring(trimmed.indexOf("://") + 3) : trimmed;
        while (host.startsWith("/")) {
            host = host.substring(1);
        }
        host = host.replaceAll("/+$", "");
        if (host.startsWith("s3.")) {
            host = host.substring(3);
        }
        return scheme + "://" + host;
    }

    static String extractHostFromEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        String host = endpoint.contains("://") ? endpoint.substring(endpoint.indexOf("://") + 3) : endpoint;
        while (host.startsWith("/")) {
            host = host.substring(1);
        }
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        return host.isBlank() ? null : host;
    }

    /**
     * 阿里云强制 virtual-hosted（返回 false）；其余 S3 兼容存储（如 MinIO）沿用 {@code configuredPathStyle}。
     */
    static boolean resolvePathStyleForSdk(String sdkEndpoint, boolean configuredPathStyle) {
        if (sdkEndpoint != null && sdkEndpoint.contains("aliyuncs.com")) {
            return false;
        }
        return configuredPathStyle;
    }

    static String resolveRequestHost(String bucket, String sdkEndpoint, boolean pathStyleAccess) {
        String host = extractHostFromEndpoint(sdkEndpoint);
        if (host == null || host.isBlank()) {
            return sdkEndpoint;
        }
        if (pathStyleAccess) {
            return host;
        }
        return bucket + "." + host;
    }

    private static Region resolveSdkRegion(String sdkEndpoint, String configuredRegion) {
        if (sdkEndpoint != null && sdkEndpoint.contains("aliyuncs.com")) {
            return Region.AWS_GLOBAL;
        }
        if (configuredRegion != null && !configuredRegion.isBlank()) {
            return Region.of(configuredRegion.trim());
        }
        return Region.US_EAST_1;
    }
}
