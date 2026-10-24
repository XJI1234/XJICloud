package com.xjicloud.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 8080 管理代理返回的 8081 云资源配置视图，字段与 8081 DTO 保持一致。 */
public final class EciManagerCloudResourceConfigDtos {

    private EciManagerCloudResourceConfigDtos() {
    }

    /** 忽略未知字段，8081 新增配置字段时不应让管理代理整体失败。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloudResourceConfigView(
            CloudResourceConfigSection network,
            CloudResourceConfigSection oss,
            CloudResourceConfigSection advanced
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloudResourceConfigSection(
            String id,
            String label,
            List<CloudResourceConfigField> fields
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloudResourceConfigField(
            String key,
            String displayName,
            String groupLabel,
            String value,
            String source,
            boolean allowEmpty,
            String valueType,
            String validationRule
    ) {
    }
}
