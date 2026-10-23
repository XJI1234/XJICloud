package com.xjicloud.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.CreateCloudAccessKeyRequest;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.UpdateCloudAccessKeyRequest;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.EciManagerProperties;
import com.xjicloud.config.SystemConfig;
import com.xjicloud.config.SystemConfigRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class CloudAccessKeyLabelServiceTest {

    private static final String ACCESS_KEY_ID = "LTAItestkey0001";
    private static final String SECRET = "test-secret-value-not-real";

    private CloudAccessKeyRepository repository;
    private SystemConfigRepository systemConfigRepository;
    private EciManagerClient eciManagerClient;
    private CloudAccessKeyService service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(CloudAccessKeyRepository.class);
        systemConfigRepository = org.mockito.Mockito.mock(SystemConfigRepository.class);
        eciManagerClient = org.mockito.Mockito.mock(EciManagerClient.class);
        when(systemConfigRepository.findById(anyString())).thenReturn(Optional.empty());
        service = new CloudAccessKeyService(
                repository,
                systemConfigRepository,
                List.of(new EciManagerApplier(eciManagerClient)),
                eciManagerClient,
                new EciManagerProperties(
                        "http://127.0.0.1:8081",
                        "test-eci-api-key",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(20)
                )
        );
        when(repository.save(any(CloudAccessKey.class))).thenAnswer(invocation -> {
            CloudAccessKey key = invocation.getArgument(0);
            if (key.getId() == null) {
                key.setId(UUID.randomUUID());
            }
            return key;
        });
    }

    @Test
    void createTrimsAndPersistsLabelWithoutSelectingCredential() {
        when(repository.existsByName("training")).thenReturn(false);

        var response = service.create(createRequest("training", "  training  "));

        ArgumentCaptor<CloudAccessKey> saved = ArgumentCaptor.forClass(CloudAccessKey.class);
        verify(repository).save(saved.capture());
        assertEquals("training", saved.getValue().getLabel());
        assertEquals("training", response.label());
        verifyNoInteractions(eciManagerClient);
        verify(systemConfigRepository, never()).save(any(SystemConfig.class));
    }

    @Test
    void createAllowsDuplicateLabels() {
        when(repository.existsByName(anyString())).thenReturn(false);

        var first = service.create(createRequest("first", "shared"));
        var second = service.create(createRequest("second", "shared"));

        assertEquals("shared", first.label());
        assertEquals("shared", second.label());
    }

    @Test
    void createStillRejectsDuplicateNames() {
        when(repository.existsByName("same-name")).thenReturn(true);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.create(createRequest("same-name", "shared-label"))
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(repository, never()).save(any(CloudAccessKey.class));
    }

    @Test
    void createWithoutLabelPersistsAndReturnsEmptyString() {
        when(repository.existsByName("unlabeled")).thenReturn(false);

        var response = service.create(createRequest("unlabeled", null));

        ArgumentCaptor<CloudAccessKey> saved = ArgumentCaptor.forClass(CloudAccessKey.class);
        verify(repository).save(saved.capture());
        assertEquals("", saved.getValue().getLabel());
        assertEquals("", response.label());
    }

    @Test
    void createAcceptsTrimmedLabelAt128CharactersAndRejects129() {
        when(repository.existsByName(anyString())).thenReturn(false);
        String maximumLabel = "x".repeat(128);

        var created = service.create(createRequest("maximum", " " + maximumLabel + " "));

        assertEquals(maximumLabel, created.label());
        BusinessException createError = assertThrows(
                BusinessException.class,
                () -> service.create(createRequest("too-long", maximumLabel + "x"))
        );
        assertEquals(HttpStatus.BAD_REQUEST, createError.getStatus());
    }

    @Test
    void updateAcceptsTrimmedLabelAt128CharactersAndRejects129() {
        String maximumLabel = "x".repeat(128);
        UUID id = UUID.randomUUID();
        CloudAccessKey existing = entity(id, "existing", "old");
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertEquals(maximumLabel, service.update(id, updateRequest(" " + maximumLabel + " ")).label());
        BusinessException updateError = assertThrows(
                BusinessException.class,
                () -> service.update(id, updateRequest(" " + maximumLabel + "x "))
        );
        assertEquals(HttpStatus.BAD_REQUEST, updateError.getStatus());
    }

    @Test
    void updateNullPreservesLabelAndBlankClearsIt() {
        UUID id = UUID.randomUUID();
        CloudAccessKey existing = entity(id, "existing", "stored");
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        var unchanged = service.update(id, updateRequest(null));
        var trimmed = service.update(id, updateRequest("  qa  "));
        var whitespaceCleared = service.update(id, updateRequest(" \t "));
        var emptyCleared = service.update(id, updateRequest(""));

        assertEquals("stored", unchanged.label());
        assertEquals("qa", trimmed.label());
        assertEquals("", whitespaceCleared.label());
        assertEquals("", emptyCleared.label());
        assertEquals("", existing.getLabel());
        verifyNoInteractions(eciManagerClient);
        verify(systemConfigRepository, never()).save(any(SystemConfig.class));
    }

    @Test
    void getMapsLegacyNullLabelToEmptyAndDoesNotExposeSecret() throws Exception {
        UUID id = UUID.randomUUID();
        CloudAccessKey legacy = entity(id, "legacy", null);
        when(repository.findById(id)).thenReturn(Optional.of(legacy));

        var response = service.get(id);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);

        assertEquals("", response.label());
        assertFalse(json.contains(SECRET));
        assertFalse(json.contains("accessKeySecret"));
    }

    private static CreateCloudAccessKeyRequest createRequest(String name, String label) {
        return new CreateCloudAccessKeyRequest(name, ACCESS_KEY_ID, SECRET, List.of("OSS"), label);
    }

    private static UpdateCloudAccessKeyRequest updateRequest(String label) {
        return new UpdateCloudAccessKeyRequest(null, null, null, null, label);
    }

    private static CloudAccessKey entity(UUID id, String name, String label) {
        CloudAccessKey key = new CloudAccessKey();
        key.setId(id);
        key.setName(name);
        key.setAccessKeyId(ACCESS_KEY_ID);
        key.setSecret(SECRET);
        key.setLabel(label);
        key.setResources(EnumSet.of(CloudResource.OSS));
        key.setCreatedAt(Instant.parse("2026-09-19T12:00:00Z"));
        key.setUpdatedAt(Instant.parse("2026-09-19T12:00:00Z"));
        return key;
    }
}
