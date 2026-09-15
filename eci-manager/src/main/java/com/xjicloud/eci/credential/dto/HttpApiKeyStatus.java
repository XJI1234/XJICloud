package com.xjicloud.eci.credential.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * HTTP Api-Key 轮换/吊销结果。永不包含明文钥。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpApiKeyStatus {

    private String apiKeyHint;
    private boolean previousActive;
    private Instant previousExpiresAt;

    public HttpApiKeyStatus() {
    }

    public HttpApiKeyStatus(String apiKeyHint, boolean previousActive, Instant previousExpiresAt) {
        this.apiKeyHint = apiKeyHint;
        this.previousActive = previousActive;
        this.previousExpiresAt = previousExpiresAt;
    }

    public String getApiKeyHint() {
        return apiKeyHint;
    }

    public void setApiKeyHint(String apiKeyHint) {
        this.apiKeyHint = apiKeyHint;
    }

    public boolean isPreviousActive() {
        return previousActive;
    }

    public void setPreviousActive(boolean previousActive) {
        this.previousActive = previousActive;
    }

    public Instant getPreviousExpiresAt() {
        return previousExpiresAt;
    }

    public void setPreviousExpiresAt(Instant previousExpiresAt) {
        this.previousExpiresAt = previousExpiresAt;
    }
}
