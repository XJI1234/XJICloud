package com.xjicloud.eci.credential;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsRequest;
import com.aliyuncs.exceptions.ClientException;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.config.OssProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 临时 STATIC_AK 测通。不走 DefaultCredentialsProvider / IMDS。
 */
@Component
public class StaticAkRuntimeCredentialProbe implements RuntimeCredentialProbe {

    private final EciProperties eciProperties;
    private final OssProperties ossProperties;
    private final StaticAkAcsClientFactory acsClientFactory;
    private final StaticAkS3ClientFactory s3ClientFactory;

    public StaticAkRuntimeCredentialProbe(
            EciProperties eciProperties,
            OssProperties ossProperties,
            StaticAkAcsClientFactory acsClientFactory,
            StaticAkS3ClientFactory s3ClientFactory) {
        this.eciProperties = eciProperties;
        this.ossProperties = ossProperties;
        this.acsClientFactory = acsClientFactory;
        this.s3ClientFactory = s3ClientFactory;
    }

    @Override
    public void probeEci(String accessKeyId, String accessKeySecret) {
        IAcsClient client = acsClientFactory.create(
                eciProperties.getRegionId(), accessKeyId, accessKeySecret);
        DescribeContainerGroupsRequest request = new DescribeContainerGroupsRequest();
        request.setRegionId(eciProperties.getRegionId());
        request.setLimit(1);
        try {
            client.getAcsResponse(request);
        } catch (ClientException ex) {
            throw new IllegalStateException("ECI 探测失败: " + ex.getMessage(), ex);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void probeOss(String accessKeyId, String accessKeySecret) {
        S3Client client = s3ClientFactory.create(ossProperties, accessKeyId, accessKeySecret);
        try {
            client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(ossProperties.getBucket())
                    .maxKeys(1)
                    .build());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 403) {
                String detail = ex.awsErrorDetails() == null
                        ? ex.getMessage()
                        : ex.awsErrorDetails().errorMessage();
                throw new IllegalStateException(
                        "OSS 鉴权失败（403）。请确认 Access Key / Secret Key。详情: " + detail,
                        ex);
            }
            throw ex;
        } catch (SdkClientException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException(
                    "OSS 连接失败，请检查 endpoint/密钥及服务器 DNS。原因: " + cause.getMessage(),
                    ex);
        } finally {
            client.close();
        }
    }
}
