package com.xjicloud.eci.oss;

import com.xjicloud.eci.config.CloudResourceConfigSnapshot;
import com.xjicloud.eci.config.CloudResourceConfigService;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.credential.AccessKeyPair;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
 * 运行时目标（enabled/endpoint/region/bucket/path-style/有效期）由候选配置一次性替换；
 * 当前 AK/SK 仍由 {@link com.xjicloud.eci.credential.RuntimeCredentialService} 持有和热切。
 * {@code enabled=false} 或目标不完整时服务仍可启动，使用 OSS 时才返回明确错误。
 */
@Service
public class OssStorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageService.class);

    private final OssProperties properties;
    private volatile ActiveClients active = ActiveClients.notConfigured();
    @Autowired(required = false)
    private CloudResourceConfigService runtimeCoordinator;

    public OssStorageService(OssProperties properties) {
        this.properties = properties;
    }

    private <T> T coordinate(Supplier<T> action) {
        if (runtimeCoordinator != null) {
            return runtimeCoordinator.coordinate(action);
        }
        synchronized (this) {
            return action.get();
        }
    }

    private void coordinate(Runnable action) {
        coordinate(() -> {
            action.run();
            return null;
        });
    }

    /**
     * 单测/显式建连：仅当 {@code enabled=true} 且 {@link OssProperties#isConfigured()} 时建客户端。
     * Spring 启动走 {@link com.xjicloud.eci.credential.RuntimeCredentialService#applyPersistedOssKeys()}。
     */
    public void init() {
        coordinate(() -> {
            if (!properties.isEnabled() || !properties.isConfigured()) {
                log.info("OSS client skipped: enabled={}, configured={}",
                        properties.isEnabled(), properties.isConfigured());
                return;
            }
            applyRuntime(prepare(
                    properties.isEnabled(),
                    properties.getEndpoint(),
                    properties.getRegion(),
                    properties.getBucket(),
                    properties.isPathStyleAccess(),
                    properties.getPresignExpirationMinutes(),
                    properties.getAccessKey(),
                    properties.getSecretKey()));
        });
    }

    /**
     * 热切换当前 OSS AccessKey；endpoint/bucket 仍来自 {@link OssProperties}。
     * 未启用或 endpoint/bucket 不完整时只记日志、不抛错。
     */
    public void applyRuntimeKeys(String accessKeyId, String accessKeySecret) {
        coordinate(() -> {
            if (!new AccessKeyPair(accessKeyId, accessKeySecret).isComplete()) {
                log.info("OSS applyRuntimeKeys skipped: access key pair incomplete");
                return;
            }
            if (!properties.isEnabled() || !properties.hasTarget()) {
                log.info("OSS applyRuntimeKeys skipped: enabled={}, endpoint/bucket incomplete",
                        properties.isEnabled());
                return;
            }
            applyRuntime(prepare(
                    properties.isEnabled(),
                    properties.getEndpoint(),
                    properties.getRegion(),
                    properties.getBucket(),
                    properties.isPathStyleAccess(),
                    properties.getPresignExpirationMinutes(),
                    accessKeyId,
                    accessKeySecret));
        });
    }

    /**
     * 用候选配置构造客户端，但不替换当前引用；未启用或目标/凭证不完整时返回未就绪候选。
     * 目标完整但 endpoint 无效等真正构造失败会抛出，由调用方在写库前回滚。
     */
    public PreparedClients prepare(
            boolean enabled,
            String endpoint,
            String region,
            String bucket,
            boolean pathStyleAccess,
            int presignExpirationMinutes,
            String accessKeyId,
            String accessKeySecret) {
        if (!enabled || !StringUtils.hasText(endpoint) || !StringUtils.hasText(bucket)) {
            return PreparedClients.notReady(endpoint, region, bucket, pathStyleAccess, presignExpirationMinutes);
        }
        if (!new AccessKeyPair(accessKeyId, accessKeySecret).isComplete()) {
            return PreparedClients.notReady(endpoint, region, bucket, pathStyleAccess, presignExpirationMinutes);
        }
        return build(endpoint, region, bucket, pathStyleAccess, presignExpirationMinutes,
                accessKeyId.trim(), accessKeySecret.trim());
    }

    /** 候选配置来自当前快照时的便捷入口。 */
    public PreparedClients prepare(CloudResourceConfigSnapshot.Oss oss, String accessKeyId, String accessKeySecret) {
        return prepare(
                oss.enabled(),
                oss.endpoint(),
                oss.region(),
                oss.bucket(),
                oss.pathStyleAccess(),
                oss.presignExpirationMinutes(),
                accessKeyId,
                accessKeySecret);
    }

    private PreparedClients build(
            String endpoint,
            String region,
            String bucket,
            boolean pathStyleAccess,
            int presignExpirationMinutes,
            String accessKey,
            String secretKey) {
        String sdkEndpoint = normalizeEndpointForSdk(endpoint);
        String sdkHost = extractHostFromEndpoint(sdkEndpoint);
        if (sdkHost == null || sdkHost.isBlank()) {
            throw new IllegalStateException("OSS endpoint 无效: " + endpoint);
        }
        Region sdkRegion = resolveSdkRegion(sdkEndpoint, region);
        boolean pathStyleForSdk = resolvePathStyleForSdk(sdkEndpoint, pathStyleAccess);
        log.info("OSS S3 client: endpoint={}, region={}, bucket={}, pathStyle={}",
                sdkEndpoint, sdkRegion, bucket, pathStyleForSdk);
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
        try {
            S3Presigner nextPresigner = S3Presigner.builder()
                    .endpointOverride(URI.create(sdkEndpoint))
                    .region(sdkRegion)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .serviceConfiguration(s3Configuration)
                    .build();
            return new PreparedClients(
                    nextClient,
                    nextPresigner,
                    sdkEndpoint,
                    region,
                    bucket,
                    pathStyleForSdk,
                    presignExpirationMinutes);
        } catch (RuntimeException ex) {
            closeQuietly(nextClient);
            throw ex;
        }
    }

    /** 一次性替换当前客户端和目标配置；旧的客户端在替换后关闭。 */
    public void applyRuntime(PreparedClients next) {
        coordinate(() -> {
            ActiveClients previous = active;
            active = new ActiveClients(
                    next.client(),
                    next.presigner(),
                    next.endpoint(),
                    next.region(),
                    next.bucket(),
                    next.pathStyleAccess(),
                    next.presignExpirationMinutes());
            closeQuietly(previous.client());
            closeQuietly(previous.presigner());
        });
    }

    /**
     * 先赋新引用再关闭旧客户端。包可见以便测试 {@code close()}。
     */
    void swapClients(S3Client nextClient, S3Presigner nextPresigner) {
        coordinate(() -> {
            applyRuntime(new PreparedClients(
                    nextClient,
                    nextPresigner,
                    properties.getEndpoint(),
                    properties.getRegion(),
                    properties.getBucket(),
                    properties.isPathStyleAccess(),
                    properties.getPresignExpirationMinutes()));
        });
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
        return coordinate(() -> {
            ActiveClients clients = requireReady();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(clients.bucket())
                    .key(ossKey)
                    .contentType(contentType)
                    .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(clients.presignExpirationMinutes()))
                    .putObjectRequest(putObjectRequest)
                    .build();
            PresignedPutObjectRequest presigned = clients.presigner().presignPutObject(presignRequest);
            return presigned.url().toString();
        });
    }

    public String presignGetUrl(String ossKey) {
        return coordinate(() -> {
            ActiveClients clients = requireReady();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(clients.presignExpirationMinutes()))
                    .getObjectRequest(builder -> builder.bucket(clients.bucket()).key(ossKey))
                    .build();
            PresignedGetObjectRequest presigned = clients.presigner().presignGetObject(presignRequest);
            return presigned.url().toString();
        });
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
     * 探测 Bucket 可达性：对当前运行时 bucket 执行 {@code ListObjectsV2 maxKeys=1}。
     * <ul>
     *   <li>客户端未初始化 → {@link IllegalStateException}（「OSS 未配置/未启用」）</li>
     *   <li>HTTP 403 → {@link IllegalStateException}（鉴权失败）</li>
     *   <li>网络/DNS 等 {@link SdkClientException} → {@link IllegalStateException}（连接失败，含请求主机提示）</li>
     *   <li>其他 {@link S3Exception} 原样抛出</li>
     * </ul>
     */
    public void testConnection() {
        coordinate(() -> {
            ActiveClients clients = requireReady();
            try {
                clients.client().listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(clients.bucket())
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
                String hostHint = resolveRequestHost(clients.bucket(), clients.endpoint(), clients.pathStyleAccess());
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                throw new IllegalStateException(
                        "OSS 连接失败，请检查 endpoint/密钥及服务器 DNS。请求主机: " + hostHint
                                + "，原因: " + cause.getMessage(),
                        ex
                );
            }
        });
    }

    private ActiveClients requireReady() {
        ActiveClients clients = active;
        if (!clients.ready()) {
            throw new IllegalStateException("OSS 未配置/未启用");
        }
        return clients;
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

    /** 候选客户端；未就绪时 client/presigner 为 null，close() 可安全用于回滚清理。 */
    public record PreparedClients(
            S3Client client,
            S3Presigner presigner,
            String endpoint,
            String region,
            String bucket,
            boolean pathStyleAccess,
            int presignExpirationMinutes) implements AutoCloseable {

        static PreparedClients notReady(
                String endpoint,
                String region,
                String bucket,
                boolean pathStyleAccess,
                int presignExpirationMinutes) {
            return new PreparedClients(null, null, endpoint, region, bucket, pathStyleAccess, presignExpirationMinutes);
        }

        @Override
        public void close() {
            closeQuietly(client);
            closeQuietly(presigner);
        }
    }

    /** 单次生效的客户端与目标配置；用单个 volatile 引用避免读到半套值。 */
    private record ActiveClients(
            S3Client client,
            S3Presigner presigner,
            String endpoint,
            String region,
            String bucket,
            boolean pathStyleAccess,
            int presignExpirationMinutes) {

        static ActiveClients notConfigured() {
            return new ActiveClients(null, null, "", "", "", false, 0);
        }

        boolean ready() {
            return client != null && presigner != null;
        }
    }
}
