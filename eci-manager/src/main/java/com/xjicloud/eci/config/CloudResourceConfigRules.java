package com.xjicloud.eci.config;

import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.provider.ShmSize;

import java.net.URI;

/** 云资源配置的文本转型和字段校验；只处理文本，不依赖页面输入组件。 */
public final class CloudResourceConfigRules {

    private CloudResourceConfigRules() {
    }

    /** 校验后的配置值：text 用于持久化和展示，runtimeValue 是运行时需要的类型值。 */
    public record ParsedValue(String text, Object runtimeValue) {

        public boolean booleanValue() {
            return (Boolean) runtimeValue;
        }

        public int intValue() {
            return (Integer) runtimeValue;
        }
    }

    public static ParsedValue parseAndValidate(CloudResourceConfigDefinition definition, String raw) {
        String text = normalize(raw);
        switch (definition.valueType()) {
            case TEXT:
                return new ParsedValue(text, text);
            case URL:
                validateEndpoint(definition, text);
                return new ParsedValue(text, text);
            case BOOLEAN: {
                boolean value = parseBoolean(definition, text);
                return new ParsedValue(Boolean.toString(value), value);
            }
            case POSITIVE_INTEGER: {
                int value = parseInteger(definition, text);
                if (value <= 0) {
                    throw invalid(definition, "必须大于 0");
                }
                return new ParsedValue(Integer.toString(value), value);
            }
            case NON_NEGATIVE_INTEGER: {
                int value = parseInteger(definition, text);
                if (value < 0) {
                    throw invalid(definition, "不能小于 0");
                }
                return new ParsedValue(Integer.toString(value), value);
            }
            case SHM_SIZE: {
                String value = parseShmSize(definition, text);
                return new ParsedValue(value, value);
            }
            default:
                throw new IllegalStateException("未知配置类型: " + definition.valueType());
        }
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static boolean parseBoolean(CloudResourceConfigDefinition definition, String text) {
        if ("true".equals(text)) {
            return true;
        }
        if ("false".equals(text)) {
            return false;
        }
        throw invalid(definition, "只能填写 true 或 false");
    }

    private static int parseInteger(CloudResourceConfigDefinition definition, String text) {
        if (text.isEmpty()) {
            throw invalid(definition, "不能为空，必须填写整数");
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw invalid(definition, "必须是整数");
        }
    }

    /** 空值保留 ECI 默认行为；有值沿用现有 shmSize 解析规则。 */
    private static String parseShmSize(CloudResourceConfigDefinition definition, String text) {
        if (text.isEmpty()) {
            return text;
        }
        try {
            return ShmSize.normalize(text);
        } catch (IllegalArgumentException ex) {
            throw invalid(definition, ex.getMessage());
        }
    }

    private static void validateEndpoint(CloudResourceConfigDefinition definition, String text) {
        if (text.isEmpty()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (IllegalArgumentException ex) {
            throw invalid(definition, "请输入有效的 URL");
        }
        if (uri.getScheme() == null || uri.getScheme().isBlank() || uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid(definition, "请输入有效的 URL");
        }
    }

    private static BusinessException invalid(CloudResourceConfigDefinition definition, String message) {
        return new BusinessException(definition.displayName() + "：" + message);
    }
}
