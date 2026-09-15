package com.xjicloud.eci.credential.dto;

/**
 * PUT {@code /credentials/http-api-key} 请求。
 */
public class PutHttpApiKeyRequest {

    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
