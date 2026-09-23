package com.xjicloud.admin;

import com.xjicloud.admin.dto.CloudAccessKeyDtos.CloudAccessKeyListResponse;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.CloudAccessKeyResponse;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.CloudAccessKeySelectResponse;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.CreateCloudAccessKeyRequest;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.SelectCloudAccessKeyRequest;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.TestCloudAccessKeyRequest;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.TestCloudAccessKeyResponse;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.UpdateCloudAccessKeyRequest;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.EciManagerProperties;
import com.xjicloud.config.SystemConfig;
import com.xjicloud.config.SystemConfigRepository;
import java.net.URI;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloudAccessKeyService {

    static final String LEGACY_CURRENT_CONFIG_KEY = "cloud.current-access-key-id";
    static final String CURRENT_CONFIG_KEY_PREFIX = "cloud.current-access-key-id.";

    private final CloudAccessKeyRepository repository;
    private final SystemConfigRepository systemConfigRepository;
    private final List<CloudAccessKeyApplier> appliers;
    private final EciManagerClient eciManagerClient;
    private final EciManagerProperties eciManagerProperties;

    public CloudAccessKeyService(
            CloudAccessKeyRepository repository,
            SystemConfigRepository systemConfigRepository,
            List<CloudAccessKeyApplier> appliers,
            EciManagerClient eciManagerClient,
            EciManagerProperties eciManagerProperties
    ) {
        this.repository = repository;
        this.systemConfigRepository = systemConfigRepository;
        this.appliers = appliers;
        this.eciManagerClient = eciManagerClient;
        this.eciManagerProperties = eciManagerProperties;
    }

    public CloudAccessKeyListResponse list(String resourceValue) {
        CloudResource resource = validateResource(resourceValue);
        Map<CloudResource, UUID> currentIds = readCurrentIds();
        List<CloudAccessKeyResponse> items = repository.findAllByResourceOrderByCreatedAtDesc(resource).stream()
                .map(key -> toResponse(key, currentIds))
                .toList();
        return new CloudAccessKeyListResponse(resource.name(), currentIds.get(resource), items);
    }

    public CloudAccessKeyResponse get(UUID id) {
        return toResponse(requireKey(id), readCurrentIds());
    }

    @Transactional
    public CloudAccessKeyResponse create(CreateCloudAccessKeyRequest request) {
        String name = requireTrimmed(request.name(), "名称不能为空");
        if (name.length() > 128) {
            throw new BusinessException("名称长度不能超过 128", HttpStatus.BAD_REQUEST);
        }
        String accessKeyId = requireCredential(
                request.accessKeyId(),
                "accessKeyId 不能为空",
                "请提供完整的 AccessKeyId，不要提交脱敏值",
                128,
                "accessKeyId 长度不能超过 128"
        );
        String secret = requireCredential(
                request.accessKeySecret(),
                "accessKeySecret 不能为空",
                "请提供完整的 AccessKeySecret，不要提交脱敏值",
                512,
                "accessKeySecret 长度不能超过 512"
        );
        Set<CloudResource> resources = requireResources(request.resources());
        if (repository.existsByName(name)) {
            throw new BusinessException("云访问密钥名称已存在", HttpStatus.CONFLICT);
        }
        CloudAccessKey key = new CloudAccessKey();
        key.setName(name);
        key.setAccessKeyId(accessKeyId);
        key.setSecret(secret);
        key.setResources(resources);
        Instant now = Instant.now();
        key.setCreatedAt(now);
        key.setUpdatedAt(now);
        return toResponse(repository.save(key), readCurrentIds());
    }

    @Transactional
    public CloudAccessKeyResponse update(UUID id, UpdateCloudAccessKeyRequest request) {
        CloudAccessKey key = requireKey(id);
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new BusinessException("名称不能为空", HttpStatus.BAD_REQUEST);
            }
            if (name.length() > 128) {
                throw new BusinessException("名称长度不能超过 128", HttpStatus.BAD_REQUEST);
            }
            if (repository.existsByNameAndIdNot(name, id)) {
                throw new BusinessException("云访问密钥名称已存在", HttpStatus.CONFLICT);
            }
            key.setName(name);
        }
        if (shouldPersistCredential(request.accessKeyId())) {
            key.setAccessKeyId(requireCredential(
                    request.accessKeyId(),
                    "accessKeyId 不能为空",
                    "请提供完整的 AccessKeyId，不要提交脱敏值",
                    128,
                    "accessKeyId 长度不能超过 128"
            ));
        }
        if (shouldPersistCredential(request.accessKeySecret())) {
            key.setSecret(requireCredential(
                    request.accessKeySecret(),
                    "accessKeySecret 不能为空",
                    "请提供完整的 AccessKeySecret，不要提交脱敏值",
                    512,
                    "accessKeySecret 长度不能超过 512"
            ));
        }
        Map<CloudResource, UUID> currentIds = readCurrentIds();
        if (request.resources() != null) {
            Set<CloudResource> resources = requireResources(request.resources());
            for (CloudResource resource : CloudResource.values()) {
                if (!resources.contains(resource) && id.equals(currentIds.get(resource))) {
                    throw new BusinessException(
                            "不能移除当前正在使用的资源绑定: " + resource.name(),
                            HttpStatus.CONFLICT
                    );
                }
            }
            key.setResources(resources);
        }
        key.setUpdatedAt(Instant.now());
        return toResponse(repository.save(key), currentIds);
    }

    @Transactional
    public void delete(UUID id) {
        CloudAccessKey key = requireKey(id);
        if (!selectedResources(id, readCurrentIds()).isEmpty()) {
            throw new BusinessException("不能删除当前选用的云访问密钥", HttpStatus.CONFLICT);
        }
        repository.delete(key);
    }

    public synchronized CloudAccessKeySelectResponse select(UUID id, SelectCloudAccessKeyRequest request) {
        CloudResource resource = validateResource(request != null ? request.resource() : null);
        CloudAccessKey key = requireKey(id);
        requireResourceBinding(key, resource);
        requireCompletePair(key);
        requireEciManagerForSelect();
        for (CloudAccessKeyApplier applier : appliers) {
            applier.apply(resource.name(), key.getAccessKeyId(), key.getSecret());
        }
        try {
            persistCurrentId(resource, id);
        } catch (RuntimeException ex) {
            throw new BusinessException(
                    "云访问密钥已推送到 eci-manager，但保存当前选用失败，请重试选用",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        return toSelectResponse(key, resource, readCurrentIds());
    }

    public TestCloudAccessKeyResponse testDraft(TestCloudAccessKeyRequest request) {
        CloudResource resource = validateResource(request.resource());
        requireEciManagerForTest();
        String accessKeyId = trimToNull(request.accessKeyId());
        String accessKeySecret = trimToNull(request.accessKeySecret());
        if (accessKeyId == null || accessKeySecret == null) {
            throw new BusinessException("accessKeyId 与 accessKeySecret 必须成对非空", HttpStatus.BAD_REQUEST);
        }
        rejectMaskedPair(accessKeyId, accessKeySecret);
        return runTest(resource, accessKeyId, accessKeySecret);
    }

    public TestCloudAccessKeyResponse testSaved(UUID id, TestCloudAccessKeyRequest request) {
        if (hasCredential(request.accessKeyId()) || hasCredential(request.accessKeySecret())) {
            throw new BusinessException("测已保存密钥时不要提交 accessKeyId 或 accessKeySecret", HttpStatus.BAD_REQUEST);
        }
        CloudResource resource = validateResource(request.resource());
        CloudAccessKey key = requireKey(id);
        requireResourceBinding(key, resource);
        requireCompletePair(key);
        requireEciManagerForTest();
        return runTest(resource, key.getAccessKeyId(), key.getSecret());
    }

    public static String maskAccessKey(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    @Transactional
    protected void persistCurrentId(CloudResource resource, UUID id) {
        String configKey = currentConfigKey(resource);
        SystemConfig config = systemConfigRepository.findById(configKey).orElseGet(SystemConfig::new);
        config.setConfigKey(configKey);
        config.setConfigValue(id.toString());
        config.setUpdatedAt(Instant.now());
        systemConfigRepository.save(config);
    }

    static String currentConfigKey(CloudResource resource) {
        return CURRENT_CONFIG_KEY_PREFIX + resource.name();
    }

    private TestCloudAccessKeyResponse runTest(
            CloudResource resource,
            String accessKeyId,
            String accessKeySecret
    ) {
        EciManagerClient.TestResult result = eciManagerClient.testRuntimeCredential(
                resource.name(),
                accessKeyId,
                accessKeySecret
        );
        return new TestCloudAccessKeyResponse(
                resource.name(),
                result.ok(),
                maskAccessKey(accessKeyId),
                result.detail() != null ? result.detail() : "",
                false,
                false
        );
    }

    private CloudAccessKey requireKey(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("云访问密钥不存在", HttpStatus.NOT_FOUND));
    }

    private Map<CloudResource, UUID> readCurrentIds() {
        Map<CloudResource, UUID> currentIds = new EnumMap<>(CloudResource.class);
        for (CloudResource resource : CloudResource.values()) {
            currentIds.put(resource, readCurrentId(resource));
        }
        return currentIds;
    }

    private UUID readCurrentId(CloudResource resource) {
        return systemConfigRepository.findById(currentConfigKey(resource))
                .map(SystemConfig::getConfigValue)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try {
                        return UUID.fromString(value);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private CloudAccessKeyResponse toResponse(
            CloudAccessKey key,
            Map<CloudResource, UUID> currentIds
    ) {
        return new CloudAccessKeyResponse(
                key.getId(),
                key.getName(),
                maskAccessKey(key.getAccessKeyId()),
                hasCredential(key.getSecret()),
                resourceNames(key.getResources()),
                selectedResources(key.getId(), currentIds),
                key.getCreatedAt(),
                key.getUpdatedAt()
        );
    }

    private CloudAccessKeySelectResponse toSelectResponse(
            CloudAccessKey key,
            CloudResource appliedResource,
            Map<CloudResource, UUID> currentIds
    ) {
        return new CloudAccessKeySelectResponse(
                key.getId(),
                key.getName(),
                maskAccessKey(key.getAccessKeyId()),
                hasCredential(key.getSecret()),
                resourceNames(key.getResources()),
                selectedResources(key.getId(), currentIds),
                key.getCreatedAt(),
                key.getUpdatedAt(),
                appliedResource.name()
        );
    }

    private void requireResourceBinding(CloudAccessKey key, CloudResource resource) {
        if (!key.getResources().contains(resource)) {
            throw new BusinessException(
                    "该云访问密钥未绑定资源: " + resource.name(),
                    HttpStatus.CONFLICT
            );
        }
    }

    private void requireCompletePair(CloudAccessKey key) {
        if (!hasCredential(key.getAccessKeyId()) || !hasCredential(key.getSecret())) {
            throw new BusinessException("该云访问密钥不完整，请先更新 AccessKeySecret", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireEciManagerForSelect() {
        if (!isEciManagerConfigured()) {
            throw new BusinessException("未配置 eci-manager 的 base-url 或 api-key，无法选用", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void requireEciManagerForTest() {
        if (!isEciManagerConfigured()) {
            throw new BusinessException("未配置 eci-manager 的 base-url 或 api-key，无法测通", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private boolean isEciManagerConfigured() {
        if (!eciManagerProperties.hasApiKey()) {
            return false;
        }
        try {
            URI uri = URI.create(eciManagerProperties.baseUrl().trim());
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Set<CloudResource> requireResources(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException("resources 至少包含一个资源", HttpStatus.BAD_REQUEST);
        }
        Set<CloudResource> resources = EnumSet.noneOf(CloudResource.class);
        for (String value : values) {
            resources.add(validateResource(value));
        }
        return resources;
    }

    private static CloudResource validateResource(String resource) {
        if (resource == null || resource.trim().isEmpty()) {
            throw new BusinessException("resource 必填", HttpStatus.BAD_REQUEST);
        }
        String trimmed = resource.trim();
        try {
            return CloudResource.valueOf(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("未知 resource: " + trimmed, HttpStatus.BAD_REQUEST);
        }
    }

    private static List<String> resourceNames(Set<CloudResource> resources) {
        return List.of(CloudResource.values()).stream()
                .filter(resources::contains)
                .map(Enum::name)
                .toList();
    }

    private static List<String> selectedResources(
            UUID keyId,
            Map<CloudResource, UUID> currentIds
    ) {
        return List.of(CloudResource.values()).stream()
                .filter(resource -> keyId != null && keyId.equals(currentIds.get(resource)))
                .map(Enum::name)
                .toList();
    }

    private static void rejectMaskedPair(String accessKeyId, String accessKeySecret) {
        if (isMaskedCredential(accessKeyId) || isMaskedCredential(accessKeySecret)) {
            throw new BusinessException(
                    "请提供完整的 AccessKeyId 与 AccessKeySecret，不要提交脱敏值",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private static String requireTrimmed(String value, String emptyMessage) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(emptyMessage, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private static String requireCredential(
            String value,
            String emptyMessage,
            String maskedMessage,
            int maxLen,
            String tooLongMessage
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(emptyMessage, HttpStatus.BAD_REQUEST);
        }
        if (isMaskedCredential(value)) {
            throw new BusinessException(maskedMessage, HttpStatus.BAD_REQUEST);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLen) {
            throw new BusinessException(tooLongMessage, HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private static boolean hasCredential(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isMaskedCredential(String value) {
        return value != null && value.contains("****");
    }

    private static boolean shouldPersistCredential(String value) {
        return hasCredential(value) && !isMaskedCredential(value);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

@Component
class CloudAccessKeyResourceMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CloudAccessKeyResourceMigration.class);

    private final CloudAccessKeyRepository repository;
    private final SystemConfigRepository systemConfigRepository;

    CloudAccessKeyResourceMigration(
            CloudAccessKeyRepository repository,
            SystemConfigRepository systemConfigRepository
    ) {
        this.repository = repository;
        this.systemConfigRepository = systemConfigRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<CloudAccessKey> migratedKeys = repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(key -> key.getResources().isEmpty())
                .peek(key -> key.setResources(EnumSet.allOf(CloudResource.class)))
                .toList();
        if (!migratedKeys.isEmpty()) {
            repository.saveAll(migratedKeys);
            log.info("Migrated {} cloud access keys to resource bindings", migratedKeys.size());
        }

        systemConfigRepository.findById(CloudAccessKeyService.LEGACY_CURRENT_CONFIG_KEY)
                .ifPresent(this::migrateLegacyCurrentId);
    }

    private void migrateLegacyCurrentId(SystemConfig legacy) {
        UUID currentId;
        try {
            currentId = UUID.fromString(legacy.getConfigValue().trim());
        } catch (RuntimeException ex) {
            log.warn("Legacy cloud access key selection is invalid; leaving it unchanged");
            return;
        }
        if (!repository.existsById(currentId)) {
            log.warn("Legacy cloud access key selection points to a missing key; leaving it unchanged");
            return;
        }
        for (CloudResource resource : CloudResource.values()) {
            String configKey = CloudAccessKeyService.currentConfigKey(resource);
            if (systemConfigRepository.existsById(configKey)) {
                continue;
            }
            SystemConfig config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(currentId.toString());
            config.setUpdatedAt(Instant.now());
            systemConfigRepository.save(config);
        }
        systemConfigRepository.delete(legacy);
        log.info("Migrated legacy cloud access key selection to resource-specific selections");
    }
}
