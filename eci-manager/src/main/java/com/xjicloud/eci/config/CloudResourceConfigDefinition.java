package com.xjicloud.eci.config;

import java.util.Arrays;
import java.util.List;

/** 云资源配置定义：内部键、环境变量名、分类、中文名称、默认值和校验规则。 */
public record CloudResourceConfigDefinition(
        CloudResourceConfigKey id,
        String key,
        String environmentName,
        CloudResourceConfigKey.Category category,
        String groupLabel,
        String displayName,
        String defaultValue,
        CloudResourceConfigKey.ValueType valueType) {

    private static final List<CloudResourceConfigDefinition> ALL =
            Arrays.stream(CloudResourceConfigKey.values()).map(CloudResourceConfigDefinition::of).toList();

    /** 集中目录：全部 15 项定义，顺序与配置键声明一致。 */
    public static List<CloudResourceConfigDefinition> all() {
        return ALL;
    }

    public static List<CloudResourceConfigDefinition> inCategory(CloudResourceConfigKey.Category category) {
        return ALL.stream().filter(definition -> definition.category() == category).toList();
    }

    public static CloudResourceConfigDefinition of(CloudResourceConfigKey id) {
        return new CloudResourceConfigDefinition(
                id,
                id.key(),
                id.environmentName(),
                id.category(),
                id.groupLabel(),
                id.displayName(),
                id.defaultValue(),
                id.valueType());
    }
}
