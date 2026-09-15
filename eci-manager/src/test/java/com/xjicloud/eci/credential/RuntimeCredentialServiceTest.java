package com.xjicloud.eci.credential;

import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.credential.dto.PutRuntimeCredentialRequest;
import com.xjicloud.eci.oss.OssStorageService;
import com.xjicloud.eci.provider.EciProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeCredentialServiceTest {

    @Mock
    private RuntimeCredentialRepository repository;
    @Mock
    private ObjectProvider<EciProvider> eciProvider;
    @Mock
    private ObjectProvider<OssStorageService> ossStorageService;

    private EciProperties eciProperties;
    private OssProperties ossProperties;
    private RecordingRuntimeCredentialProbe probe;
    private RuntimeCredentialService service;

    @BeforeEach
    void setUp() {
        eciProperties = new EciProperties();
        eciProperties.setAccessKeyId("LTAIseedeci01");
        eciProperties.setAccessKeySecret("eci-secret");
        ossProperties = new OssProperties();
        ossProperties.setAccessKey("LTAIseedoss01");
        ossProperties.setSecretKey("oss-secret");
        probe = new RecordingRuntimeCredentialProbe();
        service = new RuntimeCredentialService(
                repository, eciProperties, ossProperties, eciProvider, ossStorageService,
                new RuntimeCredentialTester(eciProperties, ossProperties, probe));
    }

    @Test
    void seedIfAbsentDoesNotOverwriteExistingRows() {
        when(repository.existsById(any())).thenReturn(true);

        service.seedIfAbsent();

        verify(repository, never()).save(any());
    }

    @Test
    void seedIfAbsentWritesMissingRows() {
        when(repository.existsById(any())).thenReturn(false);

        service.seedIfAbsent();

        verify(repository, times(4)).save(any(RuntimeCredentialEntity.class));
    }

    @Test
    void currentPairReadsIdAndSecretFromStore() {
        stubPair(AliyunResource.ECI_OPENAPI, "LTAIseedeci01", "eci-secret");

        AccessKeyPair pair = service.currentPair(AliyunResource.ECI_OPENAPI);

        assertThat(pair.isComplete()).isTrue();
        assertThat(pair.accessKeyId()).isEqualTo("LTAIseedeci01");
        assertThat(pair.accessKeySecret()).isEqualTo("eci-secret");
    }

    @Test
    void currentPairIncompleteWhenSecretMissing() {
        stubPair(AliyunResource.ECI_OPENAPI, "LTAIseedeci01", "");

        AccessKeyPair pair = service.currentPair(AliyunResource.ECI_OPENAPI);

        assertThat(pair.isComplete()).isFalse();
        assertThat(pair.accessKeyId()).isEqualTo("LTAIseedeci01");
        assertThat(service.snapshot().getResources().get(AliyunResource.ECI_OPENAPI.name()).isConfigured())
                .isFalse();
    }

    @Test
    void afterSingletonsInstantiatedAppliesCompleteOssPair() {
        when(repository.existsById(any())).thenReturn(true);
        stubPair(AliyunResource.OSS, "LTAIseedoss01", "oss-secret");
        OssStorageService oss = mock(OssStorageService.class);
        when(ossStorageService.getIfAvailable()).thenReturn(oss);

        service.afterSingletonsInstantiated();

        verify(oss).applyRuntimeKeys("LTAIseedoss01", "oss-secret");
    }

    @Test
    void testDraftDoesNotWriteOrReloadClients() {
        ossProperties.setEndpoint("https://oss-cn-hangzhou-internal.aliyuncs.com");
        ossProperties.setBucket("test-bucket");
        stubPair(AliyunResource.OSS, "LTAIseedoss01", "oss-secret");
        PutRuntimeCredentialRequest request = new PutRuntimeCredentialRequest();
        request.setResource("OSS");
        request.setAccessKeyId("LTAIdraftoss99");
        request.setAccessKeySecret("draft-oss-secret");

        var result = service.test(request);

        assertThat(result.isOk()).isTrue();
        assertThat(result.getAccessKeyHint()).isEqualTo(CredentialHint.mask("LTAIdraftoss99"));
        assertThat(probe.ossCalls()).isEqualTo(1);
        assertThat(probe.lastOssAccessKeyId()).isEqualTo("LTAIdraftoss99");
        assertThat(probe.lastOssAccessKeySecret()).isEqualTo("draft-oss-secret");
        verify(repository, never()).save(any());
        verify(eciProvider, never()).getIfAvailable();
        verify(ossStorageService, never()).getIfAvailable();
    }

    private void stubPair(AliyunResource resource, String accessKeyId, String accessKeySecret) {
        when(repository.findById(RuntimeCredentialService.idKey(resource)))
                .thenReturn(optionalRow(RuntimeCredentialService.idKey(resource), accessKeyId));
        when(repository.findById(RuntimeCredentialService.secretKey(resource)))
                .thenReturn(optionalRow(RuntimeCredentialService.secretKey(resource), accessKeySecret));
    }

    private static Optional<RuntimeCredentialEntity> optionalRow(String key, String value) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RuntimeCredentialEntity(key, value));
    }
}
