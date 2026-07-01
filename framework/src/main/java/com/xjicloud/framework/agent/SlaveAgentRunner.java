package com.xjicloud.framework.agent;

import com.xjicloud.framework.config.FrameworkProperties;
import com.xjicloud.framework.monitor.SystemMetricsService;
import com.xjicloud.framework.node.NodeRole;
import com.xjicloud.framework.node.NodeService;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
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
import org.springframework.web.client.RestTemplate;

@Component
@ConditionalOnProperty(name = "xjicloud.framework.mode", havingValue = "slave")
public class SlaveAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(SlaveAgentRunner.class);

    private final FrameworkProperties properties;
    private final SystemMetricsService metricsService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicReference<UUID> nodeId = new AtomicReference<>();

    public SlaveAgentRunner(FrameworkProperties properties, SystemMetricsService metricsService) {
        this.properties = properties;
        this.metricsService = metricsService;
    }

    @PostConstruct
    public void register() {
        if (!properties.isSlave()) {
            return;
        }
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
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
                    log.info("Registered with master as {}", nodeId.get());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to register with master: {}", e.getMessage());
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
        } catch (Exception e) {
            log.debug("Heartbeat failed: {}", e.getMessage());
        }
    }
}
