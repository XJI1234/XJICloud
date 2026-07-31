package com.xjicloud.eci.oss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xjicloud.eci.config.OssProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OssStorageServiceTest {

    @Test
    void normalizeEndpointStripsS3PrefixForAliyun() {
        assertThat(OssStorageService.normalizeEndpointForSdk(
                "https://s3.oss-cn-hangzhou.aliyuncs.com"))
                .isEqualTo("https://oss-cn-hangzhou.aliyuncs.com");
    }

    @Test
    void resolvePathStyleForSdkIsFalseOnAliyun() {
        assertThat(OssStorageService.resolvePathStyleForSdk(
                "https://oss-cn-hangzhou.aliyuncs.com", true))
                .isFalse();
    }

    @Test
    void normalizeEndpointLeavesNonAliyunUnchanged() {
        assertThat(OssStorageService.normalizeEndpointForSdk("http://127.0.0.1:9000"))
                .isEqualTo("http://127.0.0.1:9000");
    }

    @Test
    void normalizeEndpointLeavesNullAndBlank() {
        assertThat(OssStorageService.normalizeEndpointForSdk(null)).isNull();
        assertThat(OssStorageService.normalizeEndpointForSdk("")).isEmpty();
        assertThat(OssStorageService.normalizeEndpointForSdk("   ")).isEqualTo("   ");
    }

    @Test
    void normalizeEndpointOnlyStripsLeadingS3DotOnAliyun() {
        // s3.oss-cn-* 是错误前缀，须剥离；非 s3. 开头的 aliyuncs 主机保持不变
        assertThat(OssStorageService.normalizeEndpointForSdk(
                "https://oss-cn-hangzhou.aliyuncs.com"))
                .isEqualTo("https://oss-cn-hangzhou.aliyuncs.com");
    }

    @Test
    void testConnectionThrowsWhenClientNotInitialized() {
        OssStorageService service = new OssStorageService(new OssProperties());
        service.init();
        assertThatThrownBy(service::testConnection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OSS");
    }

    @Test
    void presignPutUrlUsesAws4Algorithm() {
        String url = configuredService().presignPutUrl("eci-test/k", "text/plain");
        assertThat(url).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
    }

    @Test
    void presignPutUrlContainsKeyAndSignature() {
        OssStorageService service = configuredService();
        String url = service.presignPutUrl("eci-test/k", "text/plain");
        assertThat(url).isNotBlank();
        assertThat(url).contains("eci-test/k");
        assertThat(url).containsAnyOf("X-Amz-Signature", "X-Amz-Algorithm", "Signature");
        assertThat(url).contains("test-bucket");
    }

    @Test
    void presignGetUrlContainsKeyAndSignature() {
        OssStorageService service = configuredService();
        String url = service.presignGetUrl("datasets/abc/images/1.jpg");
        assertThat(url).isNotBlank();
        assertThat(url).contains("datasets/abc/images/1.jpg");
        assertThat(url).containsAnyOf("X-Amz-Signature", "X-Amz-Algorithm", "Signature");
    }

    @Test
    void buildOutputKeyAndDatasetImageKey() {
        OssStorageService service = new OssStorageService(new OssProperties());
        UUID jobId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertThat(service.buildOutputKey(jobId))
                .isEqualTo("outputs/11111111-1111-1111-1111-111111111111/model.ply");
        assertThat(service.buildDatasetImageKey(jobId, "photo.jpg"))
                .isEqualTo("datasets/11111111-1111-1111-1111-111111111111/images/photo.jpg");
        assertThat(service.buildDatasetPrefix(jobId))
                .isEqualTo("datasets/11111111-1111-1111-1111-111111111111/");
    }

    @Test
    void presignThrowsWhenOssDisabled() {
        OssProperties props = new OssProperties();
        props.setEnabled(false);
        props.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        props.setBucket("test-bucket");
        props.setAccessKey("ak");
        props.setSecretKey("sk");
        OssStorageService service = new OssStorageService(props);
        service.init();
        assertThatThrownBy(() -> service.presignPutUrl("k", "text/plain"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OSS");
    }

    private static OssStorageService configuredService() {
        OssProperties props = new OssProperties();
        props.setEnabled(true);
        props.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        props.setRegion("cn-hangzhou");
        props.setBucket("test-bucket");
        props.setAccessKey("test-access-key");
        props.setSecretKey("test-secret-key");
        props.setPresignExpirationMinutes(60);
        OssStorageService service = new OssStorageService(props);
        service.init();
        return service;
    }
}
