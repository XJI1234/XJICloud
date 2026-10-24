package com.xjicloud.eci.credential;

import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.CloudResourceConfigService;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.credential.dto.PutRuntimeCredentialRequest;
import com.xjicloud.eci.credential.dto.RuntimeCredentialTestResult;
import com.xjicloud.eci.credential.dto.RuntimeCredentialsResponse;
import com.xjicloud.eci.credential.dto.RuntimeResourceStatus;
import com.xjicloud.eci.oss.OssStorageService;
import com.xjicloud.eci.provider.EciProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 每种资源一把当前 AK：种子、查询、PUT 落盘并热重建。GET 永不返回 SK。
 */
@Service
public class RuntimeCredentialService implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(RuntimeCredentialService.class);

    private final RuntimeCredentialRepository repository;
    private final EciProperties eciProperties;
    private final OssProperties ossProperties;
    private final ObjectProvider<EciProvider> eciProvider;
    private final ObjectProvider<OssStorageService> ossStorageService;
    private final RuntimeCredentialTester tester;
    @Autowired(required = false)
    private CloudResourceConfigService runtimeCoordinator;
    private TransactionTemplate transactionTemplate;

    public RuntimeCredentialService(
            RuntimeCredentialRepository repository,
            EciProperties eciProperties,
            OssProperties ossProperties,
            ObjectProvider<EciProvider> eciProvider,
            ObjectProvider<OssStorageService> ossStorageService,
            RuntimeCredentialTester tester) {
        this.repository = repository;
        this.eciProperties = eciProperties;
        this.ossProperties = ossProperties;
        this.eciProvider = eciProvider;
        this.ossStorageService = ossStorageService;
        this.tester = tester;
    }

    @Autowired(required = false)
    void setPlatformTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private <T> T coordinate(Supplier<T> action) {
        if (runtimeCoordinator != null) {
            return runtimeCoordinator.coordinate(action);
        }
        synchronized (this) {
            return action.get();
        }
    }

    private void coordinate(Runnable action) {
        coordinate(() -> {
            action.run();
            return null;
        });
    }

    static String idKey(AliyunResource resource) {
        return "current." + resource.name() + ".access-key-id";
    }

    static String secretKey(AliyunResource resource) {
        return "current." + resource.name() + ".secret";
    }

    /** 行不存在且 properties 非空才写入。 */
    public void seedIfAbsent() {
        coordinate(() -> {
            seedPair(AliyunResource.ECI_OPENAPI, eciProperties.getAccessKeyId(), eciProperties.getAccessKeySecret());
            seedPair(AliyunResource.OSS, ossProperties.getAccessKey(), ossProperties.getSecretKey());
        });
    }

    @Override
    public void afterSingletonsInstantiated() {
        seedIfAbsent();
        applyPersistedOssKeys();
    }

    public AccessKeyPair currentPair(AliyunResource resource) {
        return coordinate(() -> {
            return new AccessKeyPair(read(idKey(resource)), read(secretKey(resource)));
        });
    }

    /** 全部单例就绪后按库钥建 OSS 客户端（避免 {@code eciProvider} Bean 创建时 OSS 尚未存在）。 */
    public void applyPersistedOssKeys() {
        coordinate(() -> {
            AccessKeyPair pair = currentPair(AliyunResource.OSS);
            if (!pair.isComplete()) {
                return;
            }
            OssStorageService oss = ossStorageService.getIfAvailable();
            if (oss != null) {
                oss.applyRuntimeKeys(pair.accessKeyId(), pair.accessKeySecret());
            }
        });
    }

    public RuntimeCredentialsResponse snapshot() {
        return coordinate(() -> {
            Map<String, RuntimeResourceStatus> resources = new LinkedHashMap<>();
            resources.put(AliyunResource.ECI_OPENAPI.name(), eciStatus());
            resources.put(AliyunResource.OSS.name(), ossStatus());
            return new RuntimeCredentialsResponse(eciProperties.isMock(), resources);
        });
    }

    /**
     * 测通草稿或已保存当前钥。只读 {@link #currentPair}，不写库、不热切。
     */
    public RuntimeCredentialTestResult test(PutRuntimeCredentialRequest request) {
        if (request == null) {
            throw new BusinessException("resource 必填");
        }
        AliyunResource resource = parseResource(request.getResource());
        return tester.test(
                request.getResource(),
                request.getAccessKeyId(),
                request.getAccessKeySecret(),
                currentPair(resource));
    }

    public RuntimeCredentialsResponse putCurrent(PutRuntimeCredentialRequest request) {
        return coordinate(() -> {
            if (transactionTemplate != null) {
                return transactionTemplate.execute(status -> putCurrentInTransaction(request));
            }
            return putCurrentInTransaction(request);
        });
    }

    private RuntimeCredentialsResponse putCurrentInTransaction(PutRuntimeCredentialRequest request) {
        if (request == null) {
            throw new BusinessException("resource 必填");
        }
        AliyunResource resource = parseResource(request.getResource());
        AccessKeyPair pair = new AccessKeyPair(request.getAccessKeyId(), request.getAccessKeySecret());
        if (!pair.isComplete()) {
            throw new BusinessException("accessKeyId 与 accessKeySecret 必须成对非空");
        }
        String id = pair.accessKeyId().trim();
        String secret = pair.accessKeySecret().trim();
        write(idKey(resource), id);
        write(secretKey(resource), secret);
        applyToClient(resource, id, secret);
        RuntimeCredentialsResponse response = snapshot();
        RuntimeResourceStatus status = response.getResources().get(resource.name());
        log.info("runtime credential updated resource={} akHint={} mode={}",
                resource, status != null ? status.getAccessKeyHint() : "", status != null ? status.getMode() : "");
        return response;
    }

    private void applyToClient(AliyunResource resource, String accessKeyId, String accessKeySecret) {
        if (resource == AliyunResource.ECI_OPENAPI) {
            EciProvider provider = eciProvider.getIfAvailable();
            if (provider != null) {
                provider.reloadAccessKey(accessKeyId, accessKeySecret);
            }
            return;
        }
        OssStorageService oss = ossStorageService.getIfAvailable();
        if (oss != null) {
            OssStorageService.PreparedClients prepared = oss.prepare(
                    ossProperties.isEnabled(), ossProperties.getEndpoint(), ossProperties.getRegion(),
                    ossProperties.getBucket(), ossProperties.isPathStyleAccess(),
                    ossProperties.getPresignExpirationMinutes(), accessKeyId, accessKeySecret);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        oss.applyRuntime(prepared);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            prepared.close();
                        }
                    }
                });
            } else {
                oss.applyRuntime(prepared);
            }
        }
    }

    private void seedPair(AliyunResource resource, String accessKeyId, String accessKeySecret) {
        seedOne(idKey(resource), accessKeyId);
        seedOne(secretKey(resource), accessKeySecret);
    }

    private void seedOne(String key, String value) {
        if (repository.existsById(key) || !StringUtils.hasText(value)) {
            return;
        }
        repository.save(new RuntimeCredentialEntity(key, value.trim()));
    }

    private RuntimeResourceStatus eciStatus() {
        AccessKeyPair pair = currentPair(AliyunResource.ECI_OPENAPI);
        boolean configured = pair.isComplete();
        String hint = configured ? CredentialHint.mask(pair.accessKeyId()) : "";
        String mode;
        if (eciProperties.isMock()) {
            mode = "MOCK";
        } else if (configured) {
            mode = AliyunCredentialMode.STATIC_AK.name();
        } else {
            mode = AliyunCredentialResolver.resolve(
                    pair.accessKeyId(), pair.accessKeySecret(), eciProperties.getEcsRamRoleName()).name();
        }
        return new RuntimeResourceStatus(configured, hint, mode);
    }

    private RuntimeResourceStatus ossStatus() {
        AccessKeyPair pair = currentPair(AliyunResource.OSS);
        if (!pair.isComplete()) {
            return new RuntimeResourceStatus(false, "", null);
        }
        return new RuntimeResourceStatus(true, CredentialHint.mask(pair.accessKeyId()), AliyunCredentialMode.STATIC_AK.name());
    }

    private String read(String key) {
        return repository.findById(key).map(RuntimeCredentialEntity::getConfigValue).orElse("");
    }

    private void write(String key, String value) {
        RuntimeCredentialEntity entity = repository.findById(key).orElseGet(() -> new RuntimeCredentialEntity(key, value));
        entity.setConfigValue(value);
        repository.save(entity);
    }

    static AliyunResource parseResource(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("resource 必填");
        }
        try {
            return AliyunResource.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("未知 resource: " + raw.trim());
        }
    }
}
