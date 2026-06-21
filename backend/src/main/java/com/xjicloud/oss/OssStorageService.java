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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class OssStorageService {

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
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                runtimeConfig.accessKey(),
                runtimeConfig.secretKey()
        );
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(runtimeConfig.pathStyleAccess())
                .build();
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(runtimeConfig.endpoint()))
                .region(Region.of(runtimeConfig.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(URI.create(runtimeConfig.endpoint()))
                .region(Region.of(runtimeConfig.region()))
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
        map.put("accessKey", mask(runtimeConfig.accessKey()));
        map.put("pathStyleAccess", String.valueOf(runtimeConfig.pathStyleAccess()));
        return map;
    }

    @Transactional
    public void updateConfig(Map<String, String> values, String updatedBy) {
        saveConfig(KEY_ENDPOINT, values.get("endpoint"), updatedBy);
        saveConfig(KEY_REGION, values.get("region"), updatedBy);
        saveConfig(KEY_BUCKET, values.get("bucket"), updatedBy);
        saveConfig(KEY_ACCESS_KEY, values.get("accessKey"), updatedBy);
        if (values.get("secretKey") != null && !values.get("secretKey").isBlank()) {
            saveConfig(KEY_SECRET_KEY, values.get("secretKey"), updatedBy);
        }
        if (values.containsKey("pathStyleAccess")) {
            saveConfig(KEY_PATH_STYLE, values.get("pathStyleAccess"), updatedBy);
        }
        reloadClients();
    }

    public void testConnection() {
        s3Client.headBucket(HeadBucketRequest.builder().bucket(runtimeConfig.bucket()).build());
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
}
