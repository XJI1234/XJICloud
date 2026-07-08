package com.xjicloud.oss;

import com.xjicloud.config.OssProperties;
import com.xjicloud.config.SystemConfig;
import com.xjicloud.config.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
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

@Service
public class OssStorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageService.class);

    public static final String KEY_ENDPOINT = "oss.endpoint";
    public static final String KEY_REGION = "oss.region";
    public static final String KEY_BUCKET = "oss.bucket";
    public static final String KEY_ACCESS_KEY = "oss.access-key";
    public static final String KEY_SECRET_KEY = "oss.secret-key";
    public static final String KEY_PATH_STYLE = "oss.path-style-access";

    private final OssProperties defaults;
    private final SystemConfigRepository systemConfigRepository;
    private volatile OssRuntimeConfig runtimeConfig;
    private volatile S3Client s3Client;
    private volatile S3Presigner s3Presigner;

    public OssStorageService(OssProperties defaults, SystemConfigRepository systemConfigRepository) {
        this.defaults = defaults;
        this.systemConfigRepository = systemConfigRepository;
    }

    @PostConstruct
    public void init() {
        reloadClients();
    }

    public synchronized void reloadClients() {
        this.runtimeConfig = loadRuntimeConfig();
        closeClients();
        String sdkEndpoint = normalizeEndpointForSdk(runtimeConfig.endpoint());
        String sdkHost = extractHostFromEndpoint(sdkEndpoint);
        if (sdkHost == null || sdkHost.isBlank()) {
            throw new IllegalStateException("OSS endpoint 无效: " + runtimeConfig.endpoint());
        }
        Region sdkRegion = resolveSdkRegion(sdkEndpoint, runtimeConfig.region());
        boolean pathStyleForSdk = resolvePathStyleForSdk(sdkEndpoint, runtimeConfig.pathStyleAccess());
        log.info("OSS S3 client: endpoint={}, region={}, bucket={}, pathStyle={}",
                sdkEndpoint, sdkRegion, runtimeConfig.bucket(), pathStyleForSdk);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                runtimeConfig.accessKey(),
                runtimeConfig.secretKey()
        );
        // 阿里云 OSS + AWS SDK：须关闭 chunked encoding，见官方兼容文档
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleForSdk)
                .chunkedEncodingEnabled(false)
                .build();
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(sdkEndpoint))
                .region(sdkRegion)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(URI.create(sdkEndpoint))
                .region(sdkRegion)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    public OssRuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public Map<String, String> getPublicConfig() {
        Map<String, String> map = new HashMap<>();
        map.put("endpoint", runtimeConfig.endpoint());
        map.put("region", runtimeConfig.region());
        map.put("bucket", runtimeConfig.bucket());
        map.put("accessKeyConfigured", String.valueOf(hasCredential(runtimeConfig.accessKey())));
        map.put("accessKeyHint", mask(runtimeConfig.accessKey()));
        map.put("secretKeyConfigured", String.valueOf(hasCredential(runtimeConfig.secretKey())));
        map.put("pathStyleAccess", String.valueOf(runtimeConfig.pathStyleAccess()));
        return map;
    }

    @Transactional
    public void updateConfig(Map<String, String> values, String updatedBy) {
        if (values.get("endpoint") != null && !values.get("endpoint").isBlank()) {
            values.put("endpoint", normalizeEndpointForSdk(values.get("endpoint")));
        }
        saveConfig(KEY_ENDPOINT, values.get("endpoint"), updatedBy);
        saveConfig(KEY_REGION, values.get("region"), updatedBy);
        saveConfig(KEY_BUCKET, values.get("bucket"), updatedBy);
        if (shouldPersistCredential(values.get("accessKey"))) {
            saveConfig(KEY_ACCESS_KEY, values.get("accessKey"), updatedBy);
        }
        if (shouldPersistCredential(values.get("secretKey"))) {
            saveConfig(KEY_SECRET_KEY, values.get("secretKey"), updatedBy);
        }
        if (values.containsKey("pathStyleAccess")) {
            saveConfig(KEY_PATH_STYLE, values.get("pathStyleAccess"), updatedBy);
        }
        reloadClients();
    }

    public void testConnection() {
        if (isMaskedCredential(runtimeConfig.accessKey())) {
            throw new IllegalStateException(
                    "OSS Access Key 无效（可能曾被脱敏值覆盖）。请重新输入完整的 Access Key 和 Secret Key 后点击保存。"
            );
        }
        String sdkEndpoint = normalizeEndpointForSdk(runtimeConfig.endpoint());
        boolean pathStyleForSdk = resolvePathStyleForSdk(sdkEndpoint, runtimeConfig.pathStyleAccess());
        try {
            s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(runtimeConfig.bucket())
                    .maxKeys(1)
                    .build());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 403) {
                throw new IllegalStateException(
                        "OSS 鉴权失败（403）。请确认 Access Key / Secret Key 为 RAM 用户的完整明文，并重新保存。"
                        + " 若 Secret Key 留空则不会更新，需同时填写两项。详情: " + ex.awsErrorDetails().errorMessage(),
                        ex
                );
            }
            throw ex;
        } catch (SdkClientException ex) {
            String hostHint = resolveRequestHost(runtimeConfig.bucket(), sdkEndpoint, pathStyleForSdk);
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException(
                    "OSS 连接失败，请检查 endpoint/密钥及服务器 DNS。请求主机: " + hostHint + "，原因: " + cause.getMessage(),
                    ex
            );
        }
    }

    public String presignPutUrl(String ossKey, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(runtimeConfig.bucket())
                .key(ossKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(runtimeConfig.presignExpirationMinutes()))
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return presigned.url().toString();
    }

    public String presignGetUrl(String ossKey) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(runtimeConfig.presignExpirationMinutes()))
                .getObjectRequest(builder -> builder.bucket(runtimeConfig.bucket()).key(ossKey))
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

    public String buildOutputKey(UUID jobId) {
        return "outputs/" + jobId + "/model.ply";
    }

    private OssRuntimeConfig loadRuntimeConfig() {
        return new OssRuntimeConfig(
                getConfigValue(KEY_ENDPOINT, defaults.endpoint()),
                getConfigValue(KEY_REGION, defaults.region()),
                getConfigValue(KEY_BUCKET, defaults.bucket()),
                getConfigValue(KEY_ACCESS_KEY, defaults.accessKey()),
                getConfigValue(KEY_SECRET_KEY, defaults.secretKey()),
                Boolean.parseBoolean(getConfigValue(KEY_PATH_STYLE, String.valueOf(defaults.pathStyleAccess()))),
                defaults.presignExpirationMinutes()
        );
    }

    private String getConfigValue(String key, String fallback) {
        return systemConfigRepository.findById(key)
                .map(SystemConfig::getConfigValue)
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }

    private void saveConfig(String key, String value, String updatedBy) {
        if (value == null || value.isBlank()) {
            return;
        }
        SystemConfig config = systemConfigRepository.findById(key).orElseGet(SystemConfig::new);
        config.setConfigKey(key);
        config.setConfigValue(value.trim());
        config.setUpdatedAt(java.time.Instant.now());
        systemConfigRepository.save(config);
    }

    private void closeClients() {
        if (s3Presigner != null) {
            s3Presigner.close();
        }
        if (s3Client != null) {
            s3Client.close();
        }
    }

    private String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private static boolean hasCredential(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isMaskedCredential(String value) {
        return value != null && value.contains("****");
    }

    private static boolean shouldPersistCredential(String value) {
        return hasCredential(value) && !isMaskedCredential(value);
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
