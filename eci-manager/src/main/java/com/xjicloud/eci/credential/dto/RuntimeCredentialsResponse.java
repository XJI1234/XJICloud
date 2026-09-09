package com.xjicloud.eci.credential.dto;

import java.util.Map;

/**
 * GET/PUT {@code /runtime-credentials} 的 data 体。
 */
public class RuntimeCredentialsResponse {

    private boolean mock;
    private Map<String, RuntimeResourceStatus> resources;

    public RuntimeCredentialsResponse() {
    }

    public RuntimeCredentialsResponse(boolean mock, Map<String, RuntimeResourceStatus> resources) {
        this.mock = mock;
        this.resources = resources;
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public Map<String, RuntimeResourceStatus> getResources() {
        return resources;
    }

    public void setResources(Map<String, RuntimeResourceStatus> resources) {
        this.resources = resources;
    }
}
