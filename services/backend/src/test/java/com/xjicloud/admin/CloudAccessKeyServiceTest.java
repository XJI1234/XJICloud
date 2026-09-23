package com.xjicloud.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.CloudAccessKeyListResponse;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.CreateCloudAccessKeyRequest;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.SelectCloudAccessKeyRequest;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.TestCloudAccessKeyRequest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpStatus;

class CloudAccessKeyServiceTest {

    private static final String FAKE_AK = "LTAItestkey0001";
    private static final String FAKE_SK = "test-secret-value-not-real";

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
        EciManagerProperties properties = new EciManagerProperties(
                "http://127.0.0.1:8081",
                "test-eci-api-key",
                Duration.ofSeconds(3),
                Duration.ofSeconds(20)
        );
        service = new CloudAccessKeyService(
                repository,
                systemConfigRepository,
                List.of(new EciManagerApplier(eciManagerClient)),
                eciManagerClient,
                properties
        );
    }

    @Test
    void createThenResourceListContainsHintWithoutSecret() throws Exception {
        CloudAccessKey saved = newEntity("hangzhou-ram", FAKE_AK, FAKE_SK, CloudResource.OSS);
        when(repository.existsByName("hangzhou-ram")).thenReturn(false);
        when(repository.save(any(CloudAccessKey.class))).thenAnswer(inv -> {
            CloudAccessKey key = inv.getArgument(0);
            key.setId(saved.getId());
            return key;
        });
        when(repository.findAllByResourceOrderByCreatedAtDesc(CloudResource.OSS)).thenReturn(List.of(saved));

        service.create(new CreateCloudAccessKeyRequest(
                "hangzhou-ram",
                FAKE_AK,
                FAKE_SK,
                List.of("OSS")
        ));
        CloudAccessKeyListResponse list = service.list("OSS");

        assertEquals("OSS", list.resource());
        assertEquals("LT****01", list.items().get(0).accessKeyHint());
        assertEquals(List.of("OSS"), list.items().get(0).resources());
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(list);
        assertFalse(json.contains(FAKE_SK));
        assertFalse(json.contains("accessKeySecret"));
    }

    @Test
    void createMissingSecretReturns400() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.create(new CreateCloudAccessKeyRequest(
                        "n",
                        FAKE_AK,
                        "  ",
                        List.of("OSS")
                ))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("accessKeySecret 不能为空", ex.getMessage());
    }

    @Test
    void createWithoutResourcesReturns400() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.create(new CreateCloudAccessKeyRequest("n", FAKE_AK, FAKE_SK, List.of()))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("resources 至少包含一个资源", ex.getMessage());
    }

    @Test
    void updateOmittingSecretAndResourcesPreservesStoredValues() {
        UUID id = UUID.randomUUID();
        CloudAccessKey existing = newEntity("n", FAKE_AK, FAKE_SK, CloudResource.OSS);
        existing.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(CloudAccessKey.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, new UpdateCloudAccessKeyRequest(null, null, null, null));

        assertEquals(FAKE_SK, existing.getSecret());
        assertEquals(EnumSet.of(CloudResource.OSS), existing.getResources());
    }

    @Test
    void updateMaskedAccessKeyIdPreservesStoredId() {
        UUID id = UUID.randomUUID();
        CloudAccessKey existing = newEntity("n", FAKE_AK, FAKE_SK, CloudResource.OSS);
        existing.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(CloudAccessKey.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, new UpdateCloudAccessKeyRequest(null, "LT****01", null, null));

        assertEquals(FAKE_AK, existing.getAccessKeyId());
    }

    @Test
    void selectAppliesOnlyRequestedResourceThenPersistsItsCurrentId() {
        UUID id = UUID.randomUUID();
        CloudAccessKey row = newEntity("n", FAKE_AK, FAKE_SK, CloudResource.ECI_OPENAPI, CloudResource.OSS);
        row.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(row));
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.select(id, new SelectCloudAccessKeyRequest("ECI_OPENAPI"));

        verify(eciManagerClient).putRuntimeCredential("ECI_OPENAPI", FAKE_AK, FAKE_SK);
        verify(eciManagerClient, never()).putRuntimeCredential(eq("OSS"), anyString(), anyString());
        ArgumentCaptor<SystemConfig> config = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigRepository).save(config.capture());
        assertEquals("cloud.current-access-key-id.ECI_OPENAPI", config.getValue().getConfigKey());
        assertEquals("ECI_OPENAPI", response.appliedResource());
    }

    @Test
    void selectFailureDoesNotPersistCurrentId() {
        UUID id = UUID.randomUUID();
        CloudAccessKey row = newEntity("n", FAKE_AK, FAKE_SK, CloudResource.OSS);
        row.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(row));
        doThrow(new BusinessException("fail", HttpStatus.BAD_GATEWAY))
                .when(eciManagerClient).putRuntimeCredential("OSS", FAKE_AK, FAKE_SK);

        assertThrows(
                BusinessException.class,
                () -> service.select(id, new SelectCloudAccessKeyRequest("OSS"))
        );

        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    void resourceListsUseIndependentCurrentIds() {
        UUID eciId = UUID.randomUUID();
        UUID ossId = UUID.randomUUID();
        CloudAccessKey eci = newEntity("eci", FAKE_AK, FAKE_SK, CloudResource.ECI_OPENAPI);
        CloudAccessKey oss = newEntity("oss", FAKE_AK, FAKE_SK, CloudResource.OSS);
        eci.setId(eciId);
        oss.setId(ossId);
        when(repository.findAllByResourceOrderByCreatedAtDesc(CloudResource.ECI_OPENAPI))
                .thenReturn(List.of(eci));
        when(repository.findAllByResourceOrderByCreatedAtDesc(CloudResource.OSS))
                .thenReturn(List.of(oss));
        when(systemConfigRepository.findById("cloud.current-access-key-id.ECI_OPENAPI"))
                .thenReturn(Optional.of(config("cloud.current-access-key-id.ECI_OPENAPI", eciId)));
        when(systemConfigRepository.findById("cloud.current-access-key-id.OSS"))
                .thenReturn(Optional.of(config("cloud.current-access-key-id.OSS", ossId)));

        CloudAccessKeyListResponse eciList = service.list("ECI_OPENAPI");
        CloudAccessKeyListResponse ossList = service.list("OSS");

        assertEquals(eciId, eciList.currentId());
        assertEquals(List.of("ECI_OPENAPI"), eciList.items().get(0).selectedResources());
        assertEquals(ossId, ossList.currentId());
        assertEquals(List.of("OSS"), ossList.items().get(0).selectedResources());
    }

    @Test
    void selectUnboundResourceReturns409WithoutHttp() {
        UUID id = UUID.randomUUID();
        CloudAccessKey row = newEntity("n", FAKE_AK, FAKE_SK, CloudResource.OSS);
        row.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(row));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.select(id, new SelectCloudAccessKeyRequest("ECI_OPENAPI"))
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("该云访问密钥未绑定资源: ECI_OPENAPI", ex.getMessage());
        verify(eciManagerClient, never()).putRuntimeCredential(anyString(), anyString(), anyString());
    }

    @Test
    void deleteKeySelectedByAnyResourceReturns409() {
        UUID id = UUID.randomUUID();
        CloudAccessKey row = newEntity("n", FAKE_AK, FAKE_SK, CloudResource.OSS);
        row.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(row));
        when(systemConfigRepository.findById("cloud.current-access-key-id.OSS"))
                .thenReturn(Optional.of(config("cloud.current-access-key-id.OSS", id)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(id));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(repository, never()).delete(any());
    }

    @Test
    void updateCannotRemoveCurrentResourceBinding() {
        UUID id = UUID.randomUUID();
        CloudAccessKey row = newEntity(
                "n",
                FAKE_AK,
                FAKE_SK,
                CloudResource.ECI_OPENAPI,
                CloudResource.OSS
        );
        row.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(row));
        when(systemConfigRepository.findById("cloud.current-access-key-id.OSS"))
                .thenReturn(Optional.of(config("cloud.current-access-key-id.OSS", id)));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.update(
                        id,
                        new UpdateCloudAccessKeyRequest(null, null, null, List.of("ECI_OPENAPI"))
                )
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("不能移除当前正在使用的资源绑定: OSS", ex.getMessage());
    }

    @Test
    void createAndListWorkWhenApiKeyEmpty() {
        CloudAccessKeyService noKeyService = new CloudAccessKeyService(
                repository,
                systemConfigRepository,
                List.of(new EciManagerApplier(eciManagerClient)),
                eciManagerClient,
                new EciManagerProperties("http://127.0.0.1:8081", "", Duration.ofSeconds(3), Duration.ofSeconds(20))
        );
        CloudAccessKey saved = newEntity("n", FAKE_AK, FAKE_SK, CloudResource.OSS);
        when(repository.existsByName("n")).thenReturn(false);
        when(repository.save(any(CloudAccessKey.class))).thenAnswer(inv -> {
            CloudAccessKey key = inv.getArgument(0);
            key.setId(saved.getId());
            return key;
        });
        when(repository.findAllByResourceOrderByCreatedAtDesc(CloudResource.OSS)).thenReturn(List.of(saved));

        assertNotNull(noKeyService.create(new CreateCloudAccessKeyRequest(
                "n",
                FAKE_AK,
                FAKE_SK,
                List.of("OSS")
        )));
        assertEquals(1, noKeyService.list("OSS").items().size());
    }

    @Test
    void testDraftCallsTestEndpointOnly() {
        EciManagerClient.TestResult result = new EciManagerClient.TestResult(true, "ok detail");
        when(eciManagerClient.testRuntimeCredential("OSS", FAKE_AK, FAKE_SK)).thenReturn(result);

        var response = service.testDraft(new TestCloudAccessKeyRequest("OSS", FAKE_AK, FAKE_SK));

        assertTrue(response.ok());
        assertFalse(response.persistedCurrent());
        assertFalse(response.appliedToEciManager());
        verify(eciManagerClient, never()).putRuntimeCredential(anyString(), anyString(), anyString());
        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    void testSavedKeyRequiresResourceBinding() {
        UUID id = UUID.randomUUID();
        CloudAccessKey row = newEntity("n", FAKE_AK, FAKE_SK, CloudResource.OSS);
        row.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(row));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.testSaved(
                        id,
                        new TestCloudAccessKeyRequest("ECI_OPENAPI", null, null)
                )
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(eciManagerClient, never()).testRuntimeCredential(anyString(), anyString(), anyString());
    }

    @Test
    void migrationBindsLegacyKeysAndSplitsValidCurrentId() {
        UUID id = UUID.randomUUID();
        CloudAccessKey row = newEntity("real", FAKE_AK, FAKE_SK);
        row.setId(id);
        SystemConfig legacy = config(CloudAccessKeyService.LEGACY_CURRENT_CONFIG_KEY, id);
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(row));
        when(repository.existsById(id)).thenReturn(true);
        when(systemConfigRepository.findById(CloudAccessKeyService.LEGACY_CURRENT_CONFIG_KEY))
                .thenReturn(Optional.of(legacy));

        new CloudAccessKeyResourceMigration(repository, systemConfigRepository).run(null);

        assertEquals(EnumSet.allOf(CloudResource.class), row.getResources());
        verify(repository).saveAll(List.of(row));
        ArgumentCaptor<SystemConfig> migrated = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigRepository, times(2)).save(migrated.capture());
        assertEquals(
                List.of("cloud.current-access-key-id.ECI_OPENAPI", "cloud.current-access-key-id.OSS"),
                migrated.getAllValues().stream().map(SystemConfig::getConfigKey).toList()
        );
        verify(systemConfigRepository).delete(legacy);
    }

    private static CloudAccessKey newEntity(
            String name,
            String accessKeyId,
            String secret,
            CloudResource... resources
    ) {
        CloudAccessKey key = new CloudAccessKey();
        key.setId(UUID.randomUUID());
        key.setName(name);
        key.setAccessKeyId(accessKeyId);
        key.setSecret(secret);
        key.setResources(resources.length == 0
                ? EnumSet.noneOf(CloudResource.class)
                : EnumSet.copyOf(List.of(resources)));
        key.setCreatedAt(Instant.parse("2026-09-19T12:00:00Z"));
        key.setUpdatedAt(Instant.parse("2026-09-19T12:00:00Z"));
        return key;
    }

    private static SystemConfig config(String configKey, UUID keyId) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(configKey);
        config.setConfigValue(keyId.toString());
        config.setUpdatedAt(Instant.parse("2026-09-19T12:00:00Z"));
        return config;
    }
}

@DataJpaTest
class CloudAccessKeyRepositoryTest {

    @Autowired
    private CloudAccessKeyRepository repository;

    @Test
    void resourceQueryReturnsOnlyKeysBoundToRequestedResource() {
        CloudAccessKey shared = entity("shared", CloudResource.ECI_OPENAPI, CloudResource.OSS);
        CloudAccessKey ossOnly = entity("oss-only", CloudResource.OSS);
        repository.saveAllAndFlush(List.of(shared, ossOnly));

        List<CloudAccessKey> eciKeys = repository.findAllByResourceOrderByCreatedAtDesc(
                CloudResource.ECI_OPENAPI
        );
        List<CloudAccessKey> ossKeys = repository.findAllByResourceOrderByCreatedAtDesc(CloudResource.OSS);

        assertEquals(List.of("shared"), eciKeys.stream().map(CloudAccessKey::getName).toList());
        assertEquals(
                EnumSet.of(CloudResource.ECI_OPENAPI, CloudResource.OSS),
                eciKeys.get(0).getResources()
        );
        assertEquals(2, ossKeys.size());
    }

    private static CloudAccessKey entity(String name, CloudResource... resources) {
        CloudAccessKey key = new CloudAccessKey();
        key.setName(name);
        key.setAccessKeyId("LTAItestkey0001");
        key.setSecret("test-secret-value-not-real");
        key.setResources(EnumSet.copyOf(List.of(resources)));
        key.setCreatedAt(Instant.now());
        key.setUpdatedAt(Instant.now());
        return key;
    }
}
