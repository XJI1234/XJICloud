package com.xjicloud.eci.credential.dto;

/**
 * 单个 resource 的当前钥状态。永不包含 Secret。
 */
public class RuntimeResourceStatus {

    private boolean configured;
    private String accessKeyHint;
    private String mode;

    public RuntimeResourceStatus() {
    }

    public RuntimeResourceStatus(boolean configured, String accessKeyHint, String mode) {
        this.configured = configured;
        this.accessKeyHint = accessKeyHint;
        this.mode = mode;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public String getAccessKeyHint() {
        return accessKeyHint;
    }

    public void setAccessKeyHint(String accessKeyHint) {
        this.accessKeyHint = accessKeyHint;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
