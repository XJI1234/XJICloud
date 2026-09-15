package com.xjicloud.eci.credential;

import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.credential.dto.RuntimeCredentialTestResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 测通草稿或已保存当前钥。不落盘。
 */
@Component
public class RuntimeCredentialTester {

    private final EciProperties eciProperties;
    private final OssProperties ossProperties;
    private final RuntimeCredentialProbe probe;

    public RuntimeCredentialTester(EciProperties eciProperties, OssProperties ossProperties, RuntimeCredentialProbe probe) {
        this.eciProperties = eciProperties;
        this.ossProperties = ossProperties;
        this.probe = probe;
    }

    public RuntimeCredentialTestResult test(
            String resource,
            String accessKeyId,
            String accessKeySecret,
            AccessKeyPair saved) {
        AliyunResource parsed = RuntimeCredentialService.parseResource(resource);
        if (parsed == AliyunResource.ECI_OPENAPI && eciProperties.isMock()) {
            throw new BusinessException("mock 模式下不能测 ECI_OPENAPI");
        }
        if (parsed == AliyunResource.OSS && !ossProperties.hasTarget()) {
            throw new BusinessException("OSS endpoint 与 bucket 未配置，不能测通");
        }
        AccessKeyPair pair = resolvePair(accessKeyId, accessKeySecret, saved);
        String hint = CredentialHint.mask(pair.accessKeyId());
        try {
            if (parsed == AliyunResource.ECI_OPENAPI) {
                probe.probeEci(pair.accessKeyId(), pair.accessKeySecret());
            } else {
                probe.probeOss(pair.accessKeyId(), pair.accessKeySecret());
            }
            return new RuntimeCredentialTestResult(parsed.name(), true, hint, "");
        } catch (RuntimeException ex) {
            String detail = ex.getMessage() == null ? "" : ex.getMessage();
            if (StringUtils.hasText(pair.accessKeySecret()) && detail.contains(pair.accessKeySecret())) {
                detail = detail.replace(pair.accessKeySecret(), "***");
            }
            return new RuntimeCredentialTestResult(parsed.name(), false, hint, detail);
        }
    }

    private static AccessKeyPair resolvePair(String accessKeyId, String accessKeySecret, AccessKeyPair saved) {
        boolean idPresent = StringUtils.hasText(accessKeyId);
        boolean secretPresent = StringUtils.hasText(accessKeySecret);
        if (idPresent && secretPresent) {
            return new AccessKeyPair(accessKeyId.trim(), accessKeySecret.trim());
        }
        if (!idPresent && !secretPresent) {
            if (saved == null || !saved.isComplete()) {
                throw new BusinessException("当前钥未配置，不能测通");
            }
            return saved;
        }
        throw new BusinessException("accessKeyId 与 accessKeySecret 必须成对非空");
    }
}
