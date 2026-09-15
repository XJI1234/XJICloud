package com.xjicloud.eci.credential.dto;

/**
 * POST {@code /runtime-credentials/test} 的 data。永不包含 Secret。
 */
public class RuntimeCredentialTestResult {

    private String resource;
    private boolean ok;
    private String accessKeyHint;
    private String detail;

    public RuntimeCredentialTestResult() {
    }

    public RuntimeCredentialTestResult(String resource, boolean ok, String accessKeyHint, String detail) {
        this.resource = resource;
        this.ok = ok;
        this.accessKeyHint = accessKeyHint;
        this.detail = detail;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getAccessKeyHint() {
        return accessKeyHint;
    }

    public void setAccessKeyHint(String accessKeyHint) {
        this.accessKeyHint = accessKeyHint;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
