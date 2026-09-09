package com.xjicloud.eci.credential;

import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.oss.OssStorageService;
import com.xjicloud.eci.provider.EciProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.any;
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
    private RuntimeCredentialService service;

    @BeforeEach
    void setUp() {
        eciProperties = new EciProperties();
        eciProperties.setAccessKeyId("LTAIseedeci01");
        eciProperties.setAccessKeySecret("eci-secret");
        ossProperties = new OssProperties();
        ossProperties.setAccessKey("LTAIseedoss01");
        ossProperties.setSecretKey("oss-secret");
        service = new RuntimeCredentialService(
                repository, eciProperties, ossProperties, eciProvider, ossStorageService);
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
}
