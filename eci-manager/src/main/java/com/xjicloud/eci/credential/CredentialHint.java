package com.xjicloud.eci.credential;

/**
 * AccessKey 日志脱敏。永不返回完整密钥。
 */
public final class CredentialHint {

    private static final int MIN_UNMASKED_LENGTH = 12;

    private CredentialHint() {
    }

    /**
     * {@code null} 或 trim 后为空 → {@code ""}；
     * trim 后长度 {@code < 12} → {@code "****"}；
     * 否则前 4 + {@code ****} + 后 4。
     */
    public static String mask(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() < MIN_UNMASKED_LENGTH) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
