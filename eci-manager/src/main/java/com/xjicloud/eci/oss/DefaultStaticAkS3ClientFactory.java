package com.xjicloud.eci.oss;

import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.credential.AccessKeyPair;
import com.xjicloud.eci.credential.StaticAkS3ClientFactory;
import java.net.URI;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * 用指定 AccessKey 建临时 S3 客户端；不走默认凭证链。
 */
@Component
public class DefaultStaticAkS3ClientFactory implements StaticAkS3ClientFactory {

    @Override
    public S3Client create(OssProperties properties, String accessKeyId, String accessKeySecret) {
        if (!new AccessKeyPair(accessKeyId, accessKeySecret).isComplete()) {
            throw new IllegalArgumentException("accessKeyId 与 accessKeySecret 必须成对非空");
        }
        String sdkEndpoint = OssStorageService.normalizeEndpointForSdk(properties.getEndpoint());
        String sdkHost = OssStorageService.extractHostFromEndpoint(sdkEndpoint);
        if (sdkHost == null || sdkHost.isBlank()) {
            throw new IllegalStateException("OSS endpoint 无效: " + properties.getEndpoint());
        }
        Region sdkRegion = resolveSdkRegion(sdkEndpoint, properties.getRegion());
        boolean pathStyleForSdk = OssStorageService.resolvePathStyleForSdk(
                sdkEndpoint, properties.isPathStyleAccess());
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                accessKeyId.trim(), accessKeySecret.trim());
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleForSdk)
                .chunkedEncodingEnabled(false)
                .build();
        return S3Client.builder()
                .endpointOverride(URI.create(sdkEndpoint))
                .region(sdkRegion)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
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
