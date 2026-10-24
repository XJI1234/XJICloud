package com.xjicloud.eci.config.dto;

import com.xjicloud.eci.config.CloudResourceConfigDefinition;
import com.xjicloud.eci.config.CloudResourceConfigKey;
import com.xjicloud.eci.config.CloudResourceConfigSnapshot;

import java.util.List;

/** 8081 云资源配置 API 的响应体；请求值统一以字符串 Map 传入。 */
public final class CloudResourceConfigDtos {

    private CloudResourceConfigDtos() {
    }

    /** GET/PUT/DELETE 共用的完整配置视图。 */
    public record CloudResourceConfigResponse(
            CloudResourceConfigSection network,
            CloudResourceConfigSection oss,
            CloudResourceConfigSection advanced) {

        public static CloudResourceConfigResponse from(CloudResourceConfigSnapshot snapshot) {
            return new CloudResourceConfigResponse(
                    section(CloudResourceConfigKey.Category.NETWORK, snapshot),
                    section(CloudResourceConfigKey.Category.OSS, snapshot),
                    section(CloudResourceConfigKey.Category.ADVANCED, snapshot));
        }

        private static CloudResourceConfigSection section(
                CloudResourceConfigKey.Category category, CloudResourceConfigSnapshot snapshot) {
            List<CloudResourceConfigField> fields = CloudResourceConfigDefinition.inCategory(category).stream()
                    .map(definition -> field(definition, snapshot))
                    .toList();
            return new CloudResourceConfigSection(category.id(), category.label(), fields);
        }

        private static CloudResourceConfigField field(
                CloudResourceConfigDefinition definition, CloudResourceConfigSnapshot snapshot) {
            CloudResourceConfigSnapshot.ResolvedValue resolved = snapshot.values().get(definition.key());
            return new CloudResourceConfigField(
                    definition.key(),
                    definition.displayName(),
                    definition.groupLabel(),
                    resolved.value(),
                    resolved.source(),
                    definition.allowEmpty(),
                    definition.valueType().name(),
                    definition.validationRule());
        }
    }

    /** 一级分类：稳定 id、中文名称和按定义顺序排列的字段。 */
    public record CloudResourceConfigSection(
            String id,
            String label,
            List<CloudResourceConfigField> fields) {
    }

    /** 单个配置字段的展示元数据与最终文本值。 */
    public record CloudResourceConfigField(
            String key,
            String displayName,
            String groupLabel,
            String value,
            String source,
            boolean allowEmpty,
            String valueType,
            String validationRule) {
    }
}
