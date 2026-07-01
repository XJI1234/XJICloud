package com.xjicloud.frameworkintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class FrameworkClient {

    private static final Logger log = LoggerFactory.getLogger(FrameworkClient.class);

    private final FrameworkIntegrationProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public FrameworkClient(FrameworkIntegrationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(properties.enabled())
                && properties.masterUrl() != null
                && !properties.masterUrl().isBlank();
    }

    public JsonNode fetchRuntimeConfig(String ifNoneMatch) {
        if (!isEnabled()) {
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Framework-Secret", properties.apiSecret());
        if (ifNoneMatch != null) {
            headers.set("If-None-Match", ifNoneMatch);
        }
        String url = properties.masterUrl().replaceAll("/$", "") + "/api/v1/integration/runtime-config";
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            if (response.getStatusCode().value() == 304) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("data");
        } catch (Exception e) {
            log.warn("Failed to fetch framework config: {}", e.getMessage());
            return null;
        }
    }

    public void reportSyncStatus(int revision) {
        if (!isEnabled()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Framework-Secret", properties.apiSecret());
            headers.set("Content-Type", "application/json");
            String url = properties.masterUrl().replaceAll("/$", "") + "/api/v1/integration/sync-status";
            restTemplate.postForObject(url, new HttpEntity<>("{\"lastAppliedRevision\":" + revision + "}", headers), String.class);
        } catch (Exception ignored) {
        }
    }

    public boolean hasAvailableContainer() {
        if (!isEnabled()) {
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Framework-Secret", properties.apiSecret());
            String url = properties.masterUrl().replaceAll("/$", "") + "/api/v1/integration/containers/available";
            JsonNode root = objectMapper.readTree(restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            ).getBody());
            return root.path("data").path("available").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    public void requestContainer() {
        if (!isEnabled()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Framework-Secret", properties.apiSecret());
            headers.set("Content-Type", "application/json");
            String url = properties.masterUrl().replaceAll("/$", "") + "/api/v1/integration/containers";
            restTemplate.postForObject(url, new HttpEntity<>("{\"name\":\"backend-request\"}", headers), String.class);
        } catch (Exception e) {
            log.warn("Framework container request failed: {}", e.getMessage());
        }
    }
}
