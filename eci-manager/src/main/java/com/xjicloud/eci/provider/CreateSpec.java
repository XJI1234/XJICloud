package com.xjicloud.eci.provider;

import java.util.Map;

/** 传给 {@link EciProvider#create} 的创建规格（已与配置默认值合并）。 */
public record CreateSpec(
        String name,
        String image,
        double cpu,
        double memory,
        Map<String, String> env
) {
}
