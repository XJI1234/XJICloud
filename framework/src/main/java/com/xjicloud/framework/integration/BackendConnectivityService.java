package com.xjicloud.framework.integration;

import com.xjicloud.framework.config.FrameworkProperties;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@ConditionalOnProperty(name = "xjicloud.framework.mode", havingValue = "master", matchIfMissing = true)
public class BackendConnectivityService {

    private final FrameworkProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private volatile boolean reachable;
    private volatile String lastError = "尚未检测";
    private volatile long lastCheckedMs;

    public BackendConnectivityService(FrameworkProperties properties) {
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void probe() {
        lastCheckedMs = System.currentTimeMillis();
        String url = properties.backendUrl();
        if (url == null || url.isBlank()) {
            reachable = false;
            lastError = "未配置 backend-url（独立模式，可稍后部署后端后再填）";
            return;
        }
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    url.replaceAll("/$", "") + "/actuator/health", Map.class);
            reachable = response.getStatusCode().is2xxSuccessful();
            lastError = reachable ? "OK" : "HTTP " + response.getStatusCode().value();
        } catch (Exception e) {
            reachable = false;
            lastError = e.getMessage();
        }
    }

    public boolean isReachable() {
        return reachable;
    }

    public Map<String, Object> status() {
        return Map.of(
                "configured", properties.backendUrl() != null && !properties.backendUrl().isBlank(),
                "reachable", reachable,
                "backendUrl", properties.backendUrl() != null ? properties.backendUrl() : "",
                "lastError", lastError,
                "lastCheckedMs", lastCheckedMs,
                "standaloneCapable", true
        );
    }
}
