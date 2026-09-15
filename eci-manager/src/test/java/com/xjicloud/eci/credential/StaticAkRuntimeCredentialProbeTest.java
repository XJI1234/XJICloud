package com.xjicloud.eci.credential;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.auth.DefaultCredentialsProvider;
import com.aliyuncs.auth.InstanceProfileCredentialsProvider;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsRequest;
import com.aliyuncs.eci.model.v20180808.DescribeContainerGroupsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.oss.DefaultStaticAkS3ClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 生产 probe 只走 STATIC_AK factory。不 mock SUT。
 */
class StaticAkRuntimeCredentialProbeTest {

    private EciProperties eciProperties;
    private OssProperties ossProperties;
    private RecordingStaticAkAcsClientFactory acsFactory;
    private RecordingStaticAkS3ClientFactory s3Factory;
    private StaticAkRuntimeCredentialProbe probe;

    @BeforeEach
    void setUp() throws ClientException {
        eciProperties = new EciProperties();
        eciProperties.setMock(false);
        eciProperties.setRegionId("cn-hangzhou");
        ossProperties = new OssProperties();
        ossProperties.setEndpoint("https://oss-cn-hangzhou-internal.aliyuncs.com");
        ossProperties.setBucket("test-bucket");
        acsFactory = new RecordingStaticAkAcsClientFactory();
        s3Factory = new RecordingStaticAkS3ClientFactory();
        probe = new StaticAkRuntimeCredentialProbe(eciProperties, ossProperties, acsFactory, s3Factory);
    }

    @Test
    void eciProbeUsesStaticAkFactoryNotDefaultChain() {
        probe.probeEci("LTAItestkey01", "fake-secret-value");

        assertThat(acsFactory.staticAkCalls).isEqualTo(1);
        assertThat(acsFactory.defaultChainCalls).isZero();
        assertThat(acsFactory.lastRegionId).isEqualTo("cn-hangzhou");
        assertThat(acsFactory.lastAccessKeyId).isEqualTo("LTAItestkey01");
        assertThat(acsFactory.lastAccessKeySecret).isEqualTo("fake-secret-value");
        assertThat(acsFactory.lastLimit).isEqualTo(1);
        assertThat(s3Factory.staticAkCalls).isZero();
    }

    @Test
    void ossProbeUsesStaticAkFactoryNotDefaultChainAndCloses() {
        probe.probeOss("LTAItestoss01", "fake-oss-secret");

        assertThat(s3Factory.staticAkCalls).isEqualTo(1);
        assertThat(s3Factory.defaultChainCalls).isZero();
        assertThat(s3Factory.lastAccessKeyId).isEqualTo("LTAItestoss01");
        assertThat(s3Factory.lastAccessKeySecret).isEqualTo("fake-oss-secret");
        assertThat(s3Factory.lastMaxKeys).isEqualTo(1);
        assertThat(s3Factory.lastBucket).isEqualTo("test-bucket");
        assertThat(s3Factory.closeCalls).isEqualTo(1);
        assertThat(acsFactory.staticAkCalls).isZero();
        assertThat(acsFactory.defaultChainCalls).isZero();
    }

    @Test
    void eciProbeWrapsClientExceptionWithoutDefaultChain() throws ClientException {
        acsFactory.failWith(new ClientException("Forbidden.RAM"));

        assertThatThrownBy(() -> probe.probeEci("LTAItestkey01", "fake-secret-value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ECI")
                .hasMessageContaining("Forbidden.RAM");
        assertThat(acsFactory.staticAkCalls).isEqualTo(1);
        assertThat(acsFactory.defaultChainCalls).isZero();
        verify(acsFactory.client).shutdown();
    }

    @Test
    void ossProbeClosesAfter403() {
        s3Factory.listFailure = S3Exception.builder()
                .statusCode(403)
                .message("Access Denied")
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("Access Denied").build())
                .build();

        assertThatThrownBy(() -> probe.probeOss("LTAItestoss01", "fake-oss-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("403")
                .hasMessageNotContaining("fake-oss-secret");
        assertThat(s3Factory.closeCalls).isEqualTo(1);
        assertThat(s3Factory.defaultChainCalls).isZero();
    }

    @Test
    void acsFactoryCreateUsesStaticProfileNotDefaultProvider() {
        DefaultStaticAkAcsClientFactory factory = new DefaultStaticAkAcsClientFactory();
        IAcsClient client = factory.create("cn-hangzhou", "LTAItestkey01", "fake-secret-value");
        try {
            DefaultAcsClient acs = (DefaultAcsClient) client;
            assertThat(acs.getCredentialsProvider())
                    .isNotInstanceOf(DefaultCredentialsProvider.class)
                    .isNotInstanceOf(InstanceProfileCredentialsProvider.class);
            assertThat(acs.getProfile().getRegionId()).isEqualTo("cn-hangzhou");
            assertThat(acs.getProfile().getCredential().getAccessKeyId()).isEqualTo("LTAItestkey01");
        } finally {
            client.shutdown();
        }
    }

    @Test
    void acsFactoryRejectsIncompletePair() {
        DefaultStaticAkAcsClientFactory factory = new DefaultStaticAkAcsClientFactory();
        assertThatThrownBy(() -> factory.create("cn-hangzhou", "LTAItestkey01", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成对");
        assertThatThrownBy(() -> factory.create("cn-hangzhou", "", "fake-secret-value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成对");
    }

    @Test
    void s3FactoryRejectsIncompletePair() {
        DefaultStaticAkS3ClientFactory factory = new DefaultStaticAkS3ClientFactory();
        assertThatThrownBy(() -> factory.create(ossProperties, "LTAItestoss01", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成对");
        assertThatThrownBy(() -> factory.create(ossProperties, "", "fake-oss-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成对");
    }

    private static final class RecordingStaticAkAcsClientFactory implements StaticAkAcsClientFactory {
        int staticAkCalls;
        int defaultChainCalls;
        String lastRegionId;
        String lastAccessKeyId;
        String lastAccessKeySecret;
        Integer lastLimit;
        final IAcsClient client = mock(IAcsClient.class);

        RecordingStaticAkAcsClientFactory() throws ClientException {
            succeed();
        }

        void succeed() throws ClientException {
            doAnswer(invocation -> {
                DescribeContainerGroupsRequest request = invocation.getArgument(0);
                lastLimit = request.getLimit();
                return new DescribeContainerGroupsResponse();
            }).when(client).getAcsResponse(any(DescribeContainerGroupsRequest.class));
        }

        void failWith(ClientException failure) throws ClientException {
            doAnswer(invocation -> {
                throw failure;
            }).when(client).getAcsResponse(any(DescribeContainerGroupsRequest.class));
        }

        @Override
        public IAcsClient create(String regionId, String accessKeyId, String accessKeySecret) {
            staticAkCalls++;
            lastRegionId = regionId;
            lastAccessKeyId = accessKeyId;
            lastAccessKeySecret = accessKeySecret;
            return client;
        }

        IAcsClient createDefaultChain(String regionId) {
            defaultChainCalls++;
            lastRegionId = regionId;
            return client;
        }
    }

    private static final class RecordingStaticAkS3ClientFactory implements StaticAkS3ClientFactory {
        int staticAkCalls;
        int defaultChainCalls;
        int closeCalls;
        String lastAccessKeyId;
        String lastAccessKeySecret;
        Integer lastMaxKeys;
        String lastBucket;
        RuntimeException listFailure;

        @Override
        public S3Client create(OssProperties properties, String accessKeyId, String accessKeySecret) {
            staticAkCalls++;
            lastAccessKeyId = accessKeyId;
            lastAccessKeySecret = accessKeySecret;
            S3Client client = mock(S3Client.class);
            doAnswer(invocation -> {
                ListObjectsV2Request request = invocation.getArgument(0);
                lastMaxKeys = request.maxKeys();
                lastBucket = request.bucket();
                if (listFailure != null) {
                    throw listFailure;
                }
                return ListObjectsV2Response.builder().build();
            }).when(client).listObjectsV2(any(ListObjectsV2Request.class));
            doAnswer(invocation -> {
                closeCalls++;
                return null;
            }).when(client).close();
            return client;
        }

        S3Client createDefaultChain(OssProperties properties) {
            defaultChainCalls++;
            return mock(S3Client.class);
        }
    }
}
