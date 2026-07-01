package com.xjicloud.framework.configregistry;

final class SecretMask {
    private SecretMask() {}

    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 4) {
            return "***";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
