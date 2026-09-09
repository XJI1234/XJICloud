package com.xjicloud.eci.credential.dto;

/**
 * PUT {@code /runtime-credentials} 请求。{@code resource} 为字符串，未知值由服务层转 400。
 */
public class PutRuntimeCredentialRequest {

    private String resource;
    private String accessKeyId;
    private String accessKeySecret;

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }
}
