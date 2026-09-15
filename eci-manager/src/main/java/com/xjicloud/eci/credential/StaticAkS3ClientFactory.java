package com.xjicloud.eci.credential;

import com.xjicloud.eci.config.OssProperties;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 用指定 AccessKey 建临时 S3 客户端；endpoint/bucket 仍来自 {@link OssProperties}。
 */
public interface StaticAkS3ClientFactory {

    S3Client create(OssProperties properties, String accessKeyId, String accessKeySecret);
}
