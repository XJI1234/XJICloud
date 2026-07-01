package com.xjicloud.framework.agent;

import com.xjicloud.framework.config.FrameworkProperties;
import com.xjicloud.framework.monitor.SystemMetricsService;
import com.xjicloud.framework.node.NodeRole;
import com.xjicloud.framework.node.NodeService;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
@ConditionalOnProperty(name = "xjicloud.framework.mode", havingValue = "slave")
public class SlaveAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(SlaveAgentRunner.class);

    private final FrameworkProperties properties;
    private final SystemMetricsService metricsService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicReference<UUID> nodeId = new AtomicReference<>();
    private volatile String lastRegisterError = "";

    public SlaveAgentRunner(FrameworkProperties properties, SystemMetricsService metricsService) {
        this.properties = properties;
        this.metricsService = metricsService;
    }

    @PostConstruct
    public void register() {
        tryRegister();
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void retryRegisterIfNeeded() {
        if (nodeId.get() == null) {
            tryRegister();
        }
    }

    private void tryRegister() {
        if (!properties.isSlave()) {
            return;
        }
        String host = AdvertiseHostResolver.resolve(properties.advertiseHost());
        try {
            String metrics = metricsService.collectMetricsJson();
            var request = new NodeService.AgentRegisterRequest(host, host, NodeRole.CUSTOM, metrics);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Agent-Token", properties.agentToken());
            String url = properties.masterUrl().replaceAll("/$", "") + "/api/v1/agent/register";
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(url, new HttpEntity<>(request, headers), Map.class);
            if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data != null && data.get("nodeId") != null) {
                    nodeId.set(UUID.fromString(data.get("nodeId").toString()));
                    lastRegisterError = "";
                    log.info("Registered with master as {} (advertise-host={})", nodeId.get(), host);
                    return;
                }
            }
            lastRegisterError = body != null ? String.valueOf(body.get("message")) : "empty response";
            log.warn("Master rejected registration for host {}: {}", host, lastRegisterError);
        } catch (HttpStatusCodeException e) {
            lastRegisterError = e.getStatusCode() + " " + e.getResponseBodyAsString();
            log.warn("Failed to register with master (host={}): {}", host, lastRegisterError);
        } catch (Exception e) {
            lastRegisterError = e.getMessage();
            log.warn("Failed to register with master (host={}): {}", host, lastRegisterError);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        UUID id = nodeId.get();
        if (id == null || !properties.isSlave()) {
            return;
        }
        try {
            var request = new NodeService.AgentHeartbeatRequest(metricsService.collectMetricsJson(), 0);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Agent-Token", properties.agentToken());
            String url = properties.masterUrl().replaceAll("/$", "") + "/api/v1/agent/" + id + "/heartbeat";
            restTemplate.postForObject(url, new HttpEntity<>(request, headers), Map.class);
        } catch (HttpStatusCodeException e) {
            log.warn("Heartbeat rejected for {}: {} {}", id, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Heartbeat failed for {}: {}", id, e.getMessage());
        }
    }

    public String lastRegisterError() {
        return lastRegisterError;
    }

    public UUID registeredNodeId() {
        return nodeId.get();
    }

    public String resolvedAdvertiseHost() {
        return AdvertiseHostResolver.resolve(properties.advertiseHost());
    }
}
