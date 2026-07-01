package com.xjicloud.framework.configregistry;

import com.xjicloud.framework.common.ApiResponse;
import com.xjicloud.framework.common.BusinessException;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.DatabaseConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.OssConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.RedisConfig;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@RestController
@RequestMapping("/api/v1/config")
public class RuntimeConfigController {

    private final RuntimeConfigService configService;
    private final SecretCrypto crypto;

    public RuntimeConfigController(RuntimeConfigService configService, SecretCrypto crypto) {
        this.configService = configService;
        this.crypto = crypto;
    }

    @GetMapping
    public ApiResponse<ConfigView> getConfig() {
        RuntimeConfigService.RuntimeConfigSnapshot snap = configService.getSnapshot();
        return ApiResponse.ok(new ConfigView(snap.revision(), snap.updatedAt(), configService.getPublicDocument()));
    }

    @PutMapping
    public ApiResponse<ConfigView> updateConfig(@RequestBody RuntimeConfigDocument body) {
        RuntimeConfigService.RuntimeConfigSnapshot snap = configService.update(body, true);
        return ApiResponse.ok(new ConfigView(snap.revision(), snap.updatedAt(), configService.getPublicDocument()));
    }

    @PostMapping("/test/database")
    public ApiResponse<Void> testDatabase(@RequestBody DatabaseConfig body) {
        String password = body.password();
        if (password != null && password.contains("***")) {
            password = crypto.decrypt(configService.getSnapshot().config().database().password());
        }
        try (Connection conn = DriverManager.getConnection(body.url(), body.username(), password)) {
            return ApiResponse.ok("数据库连接成功", null);
        } catch (Exception e) {
            throw new BusinessException("数据库连接失败: " + e.getMessage());
        }
    }

    @PostMapping("/test/redis")
    public ApiResponse<Void> testRedis(@RequestBody RedisConfig body) {
        String password = body.password();
        if (password != null && password.contains("***")) {
            password = crypto.decrypt(configService.getSnapshot().config().redis().password());
        }
        try (Jedis jedis = new Jedis(
                new HostAndPort(body.host(), body.port()),
                DefaultJedisClientConfig.builder().password(password == null || password.isBlank() ? null : password).build()
        )) {
            jedis.ping();
            return ApiResponse.ok("Redis 连接成功", null);
        } catch (Exception e) {
            throw new BusinessException("Redis 连接失败: " + e.getMessage());
        }
    }

    @PostMapping("/test/oss")
    public ApiResponse<Void> testOss(@RequestBody OssConfig body) {
        String secretKey = body.secretKey();
        if (secretKey != null && secretKey.contains("***")) {
            secretKey = crypto.decrypt(configService.getSnapshot().config().oss().secretKey());
        }
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(body.endpoint()))
                .region(Region.of(body.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(body.accessKey(), secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(Boolean.TRUE.equals(body.pathStyleAccess()))
                        .build())
                .build()) {
            client.headBucket(HeadBucketRequest.builder().bucket(body.bucket()).build());
            return ApiResponse.ok("OSS 连接成功", null);
        } catch (Exception e) {
            throw new BusinessException("OSS 连接失败: " + e.getMessage());
        }
    }

    public record ConfigView(int revision, Instant updatedAt, RuntimeConfigDocument config) {}
}
