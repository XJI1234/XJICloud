package com.xjicloud.eci.credential;

import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.credential.dto.PutRuntimeCredentialRequest;
import com.xjicloud.eci.credential.dto.RuntimeCredentialsResponse;
import com.xjicloud.eci.credential.dto.RuntimeResourceStatus;
import com.xjicloud.eci.oss.OssStorageService;
import com.xjicloud.eci.provider.EciProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 每种资源一把当前 AK：种子、查询、PUT 落盘并热重建。GET 永不返回 SK。
 */
@Service
public class RuntimeCredentialService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeCredentialService.class);

    private final RuntimeCredentialRepository repository;
    private final EciProperties eciProperties;
    private final OssProperties ossProperties;
    private final ObjectProvider<EciProvider> eciProvider;
    private final ObjectProvider<OssStorageService> ossStorageService;

    public RuntimeCredentialService(
            RuntimeCredentialRepository repository,
            EciProperties eciProperties,
            OssProperties ossProperties,
            ObjectProvider<EciProvider> eciProvider,
            ObjectProvider<OssStorageService> ossStorageService) {
        this.repository = repository;
        this.eciProperties = eciProperties;
        this.ossProperties = ossProperties;
        this.eciProvider = eciProvider;
        this.ossStorageService = ossStorageService;
    }

    static String idKey(AliyunResource resource) {
        return "current." + resource.name() + ".access-key-id";
    }

    static String secretKey(AliyunResource resource) {
        return "current." + resource.name() + ".secret";
    }

    /** 行不存在且 properties 非空才写入。 */
    public void seedIfAbsent() {
        seedPair(AliyunResource.ECI_OPENAPI, eciProperties.getAccessKeyId(), eciProperties.getAccessKeySecret());
        seedPair(AliyunResource.OSS, ossProperties.getAccessKey(), ossProperties.getSecretKey());
    }

    /** 用库中当前 ECI 成对钥覆盖 properties，供 AliyunEciProvider 构造。 */
    public void overlayEciKeys(EciProperties properties) {
        String id = read(idKey(AliyunResource.ECI_OPENAPI));
        String secret = read(secretKey(AliyunResource.ECI_OPENAPI));
        if (StringUtils.hasText(id) && StringUtils.hasText(secret)) {
            properties.setAccessKeyId(id);
            properties.setAccessKeySecret(secret);
        }
    }

    /** 启动后按库钥重建 OSS 客户端（{@code @PostConstruct} 可能早于 seed）。 */
    public void applyPersistedOssKeys() {
        String id = read(idKey(AliyunResource.OSS));
        String secret = read(secretKey(AliyunResource.OSS));
        if (!StringUtils.hasText(id) || !StringUtils.hasText(secret)) {
            return;
        }
        OssStorageService oss = ossStorageService.getIfAvailable();
        if (oss != null) {
            oss.applyRuntimeKeys(id, secret);
        }
    }

    public RuntimeCredentialsResponse snapshot() {
        Map<String, RuntimeResourceStatus> resources = new LinkedHashMap<>();
        resources.put(AliyunResource.ECI_OPENAPI.name(), eciStatus());
        resources.put(AliyunResource.OSS.name(), ossStatus());
        return new RuntimeCredentialsResponse(eciProperties.isMock(), resources);
    }

    @Transactional
    public RuntimeCredentialsResponse putCurrent(PutRuntimeCredentialRequest request) {
        if (request == null) {
            throw new BusinessException("resource 必填");
        }
        AliyunResource resource = parseResource(request.getResource());
        String accessKeyId = request.getAccessKeyId();
        String accessKeySecret = request.getAccessKeySecret();
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
            throw new BusinessException("accessKeyId 与 accessKeySecret 必须成对非空");
        }
        String id = accessKeyId.trim();
        String secret = accessKeySecret.trim();
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
            oss.applyRuntimeKeys(accessKeyId, accessKeySecret);
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
        String id = read(idKey(AliyunResource.ECI_OPENAPI));
        String secret = read(secretKey(AliyunResource.ECI_OPENAPI));
        boolean configured = StringUtils.hasText(id) && StringUtils.hasText(secret);
        String hint = configured ? CredentialHint.mask(id) : "";
        String mode;
        if (eciProperties.isMock()) {
            mode = "MOCK";
        } else if (configured) {
            mode = AliyunCredentialMode.STATIC_AK.name();
        } else {
            mode = AliyunCredentialResolver.resolve(id, secret, eciProperties.getEcsRamRoleName()).name();
        }
        return new RuntimeResourceStatus(configured, hint, mode);
    }

    private RuntimeResourceStatus ossStatus() {
        String id = read(idKey(AliyunResource.OSS));
        String secret = read(secretKey(AliyunResource.OSS));
        boolean configured = StringUtils.hasText(id) && StringUtils.hasText(secret);
        if (!configured) {
            return new RuntimeResourceStatus(false, "", null);
        }
        return new RuntimeResourceStatus(true, CredentialHint.mask(id), AliyunCredentialMode.STATIC_AK.name());
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
