package com.xjicloud.eci.credential;

import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.credential.dto.RuntimeCredentialTestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 测通规则：校验失败不调 probe。不 mock SUT。
 */
class RuntimeCredentialTesterTest {

    private EciProperties eciProperties;
    private OssProperties ossProperties;
    private RecordingRuntimeCredentialProbe probe;
    private RuntimeCredentialTester tester;

    @BeforeEach
    void setUp() {
        eciProperties = new EciProperties();
        eciProperties.setMock(true);
        ossProperties = new OssProperties();
        probe = new RecordingRuntimeCredentialProbe();
        tester = new RuntimeCredentialTester(eciProperties, ossProperties, probe);
    }

    @Test
    void mockEciRejectsWithoutCallingProbe() {
        AccessKeyPair saved = new AccessKeyPair("LTAIsavedeci01", "saved-eci-secret");

        assertThatThrownBy(() -> tester.test("ECI_OPENAPI", "LTAIdraftkey01", "draft-secret-value", saved))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mock")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(probe.eciCalls()).isZero();
        assertThat(probe.ossCalls()).isZero();
    }

    @Test
    void ossWithoutTargetRejectsWithoutCallingProbe() {
        eciProperties.setMock(false);
        ossProperties.setEndpoint("");
        ossProperties.setBucket("");

        assertThatThrownBy(() -> tester.test("OSS", "LTAIdraftoss01", "draft-oss-secret", new AccessKeyPair("", "")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("endpoint")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(probe.ossCalls()).isZero();
        assertThat(probe.eciCalls()).isZero();
    }

    @Test
    void unknownResourceRejectsWithoutCallingProbe() {
        assertThatThrownBy(() -> tester.test("NOT_A_RESOURCE", "LTAIdraftkey01", "draft-secret-value", new AccessKeyPair("", "")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知 resource")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(probe.eciCalls()).isZero();
    }

    @Test
    void halfPairRejectsWithoutCallingProbe() {
        ossProperties.setEndpoint("https://oss-cn-hangzhou-internal.aliyuncs.com");
        ossProperties.setBucket("test-bucket");

        assertThatThrownBy(() -> tester.test("OSS", "LTAIdraftoss01", "", new AccessKeyPair("LTAIsavedoss01", "saved-oss-secret")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("成对非空")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(probe.ossCalls()).isZero();
    }

    @Test
    void omittedPairWithIncompleteSavedRejectsWithoutCallingProbe() {
        ossProperties.setEndpoint("https://oss-cn-hangzhou-internal.aliyuncs.com");
        ossProperties.setBucket("test-bucket");

        assertThatThrownBy(() -> tester.test("OSS", null, null, new AccessKeyPair("LTAIsavedoss01", "")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前钥")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(probe.ossCalls()).isZero();
    }

    @Test
    void blankResourceRejectsWithoutCallingProbe() {
        assertThatThrownBy(() -> tester.test("  ", "LTAIdraftkey01", "draft-secret-value", new AccessKeyPair("", "")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("resource")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(probe.eciCalls()).isZero();
    }

    @Test
    void draftOssProbesDraftNotSaved() {
        ossProperties.setEndpoint("https://oss-cn-hangzhou-internal.aliyuncs.com");
        ossProperties.setBucket("test-bucket");
        AccessKeyPair saved = new AccessKeyPair("LTAIsavedoss01", "saved-oss-secret");

        RuntimeCredentialTestResult result = tester.test("OSS", "LTAIdraftoss01", "draft-oss-secret", saved);

        assertThat(result.getResource()).isEqualTo("OSS");
        assertThat(result.isOk()).isTrue();
        assertThat(result.getAccessKeyHint()).isEqualTo(CredentialHint.mask("LTAIdraftoss01"));
        assertThat(result.getDetail()).isEmpty();
        assertThat(probe.ossCalls()).isEqualTo(1);
        assertThat(probe.lastOssAccessKeyId()).isEqualTo("LTAIdraftoss01");
        assertThat(probe.lastOssAccessKeySecret()).isEqualTo("draft-oss-secret");
        assertThat(probe.eciCalls()).isZero();
        assertThat(result.getAccessKeyHint()).doesNotContain("draft-oss-secret");
    }

    @Test
    void omittedOssProbesSavedPair() {
        ossProperties.setEndpoint("https://oss-cn-hangzhou-internal.aliyuncs.com");
        ossProperties.setBucket("test-bucket");
        AccessKeyPair saved = new AccessKeyPair("LTAIsavedoss01", "saved-oss-secret");

        RuntimeCredentialTestResult result = tester.test("OSS", null, null, saved);

        assertThat(result.isOk()).isTrue();
        assertThat(result.getAccessKeyHint()).isEqualTo(CredentialHint.mask("LTAIsavedoss01"));
        assertThat(probe.ossCalls()).isEqualTo(1);
        assertThat(probe.lastOssAccessKeyId()).isEqualTo("LTAIsavedoss01");
        assertThat(probe.lastOssAccessKeySecret()).isEqualTo("saved-oss-secret");
        assertThat(probe.eciCalls()).isZero();
    }

    @Test
    void eciDraftProbesWhenNotMock() {
        eciProperties.setMock(false);
        AccessKeyPair saved = new AccessKeyPair("LTAIsavedeci01", "saved-eci-secret");

        RuntimeCredentialTestResult result = tester.test("ECI_OPENAPI", "LTAIdraftkey01", "draft-secret-value", saved);

        assertThat(result.getResource()).isEqualTo("ECI_OPENAPI");
        assertThat(result.isOk()).isTrue();
        assertThat(result.getAccessKeyHint()).isEqualTo(CredentialHint.mask("LTAIdraftkey01"));
        assertThat(probe.eciCalls()).isEqualTo(1);
        assertThat(probe.lastEciAccessKeyId()).isEqualTo("LTAIdraftkey01");
        assertThat(probe.lastEciAccessKeySecret()).isEqualTo("draft-secret-value");
        assertThat(probe.ossCalls()).isZero();
        assertThat(result.getAccessKeyHint()).doesNotContain("draft-secret-value");
    }

    @Test
    void ossProbeFailureReturnsOkFalseWithoutSecret() {
        ossProperties.setEndpoint("https://oss-cn-hangzhou-internal.aliyuncs.com");
        ossProperties.setBucket("test-bucket");
        probe.failOss(new IllegalStateException("OSS 鉴权失败（403）。secret=should-not-leak"));

        RuntimeCredentialTestResult result = tester.test("OSS", "LTAIdraftoss01", "draft-oss-secret", new AccessKeyPair("", ""));

        assertThat(result.isOk()).isFalse();
        assertThat(result.getResource()).isEqualTo("OSS");
        assertThat(result.getAccessKeyHint()).isEqualTo(CredentialHint.mask("LTAIdraftoss01"));
        assertThat(result.getDetail()).contains("403");
        assertThat(result.getDetail()).doesNotContain("draft-oss-secret");
        assertThat(probe.ossCalls()).isEqualTo(1);
    }
}
