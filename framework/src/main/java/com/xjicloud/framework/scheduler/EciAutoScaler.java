package com.xjicloud.framework.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.framework.config.FrameworkProperties;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.BackendConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigService;
import com.xjicloud.framework.eci.EciInstanceService;
import com.xjicloud.framework.eci.EciStatus;
import com.xjicloud.framework.eci.EciInstanceRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@ConditionalOnProperty(name = "xjicloud.framework.mode", havingValue = "master", matchIfMissing = true)
public class EciAutoScaler {

    private static final Logger log = LoggerFactory.getLogger(EciAutoScaler.class);

    private final FrameworkProperties properties;
    private final RuntimeConfigService configService;
    private final EciInstanceService eciService;
    private final EciInstanceRepository eciRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public EciAutoScaler(
            FrameworkProperties properties,
            RuntimeConfigService configService,
            EciInstanceService eciService,
            EciInstanceRepository eciRepository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.configService = configService;
        this.eciService = eciService;
        this.eciRepository = eciRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 30000)
    public void scale() {
        if (properties.aliyun() == null || !properties.aliyun().autoScaleEnabled()) {
            return;
        }
        try {
            QueueStats stats = fetchQueueStats();
            if (stats == null) return;

            long activeEci = eciRepository.findByStatusIn(List.of(EciStatus.RUNNING, EciStatus.PENDING)).size();
            boolean hasIdleWorker = stats.idleWorkers() > 0 || stats.onlineWorkers() > 0;

            if (stats.queueDepth() > 0 && !hasIdleWorker && activeEci == 0) {
                log.info("Auto-scaling: creating ECI instance, queueDepth={}", stats.queueDepth());
                eciService.createInstance("auto-eci-" + System.currentTimeMillis());
            }

            if (stats.queueDepth() == 0 && stats.runningJobs() == 0 && activeEci > 0) {
                int delayMin = properties.aliyun().scaleDownDelayMinutes();
                eciRepository.findByStatusIn(List.of(EciStatus.RUNNING, EciStatus.PENDING)).forEach(eci -> {
                    if (eci.getUpdatedAt().isBefore(Instant.now().minus(delayMin, ChronoUnit.MINUTES))) {
                        try {
                            eciService.stopInstance(eci.getId());
                        } catch (Exception e) {
                            log.debug("Scale down failed: {}", e.getMessage());
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.debug("Auto-scale tick failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private QueueStats fetchQueueStats() {
        BackendConfig backend = configService.getSnapshot().config().backend();
        String url = (backend != null && backend.publicUrl() != null ? backend.publicUrl() : properties.backendUrl())
                .replaceAll("/$", "") + "/api/v1/framework/queue-stats";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Framework-Secret", properties.backendApiSecret());
        try {
            Map<String, Object> body = restTemplate.postForObject(url, new HttpEntity<>(headers), Map.class);
            if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
                Map<String, Object> getBody = restTemplate.getForObject(url, Map.class);
                body = getBody;
            }
            if (body == null || body.get("data") == null) return null;
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return new QueueStats(
                    toLong(data.get("queueDepth")),
                    toLong(data.get("runningJobs")),
                    toLong(data.get("onlineWorkers")),
                    toLong(data.get("idleWorkers"))
            );
        } catch (Exception e) {
            try {
                HttpHeaders h = new HttpHeaders();
                h.set("X-Framework-Secret", properties.backendApiSecret());
                Map<String, Object> body = restTemplate.exchange(
                        url, org.springframework.http.HttpMethod.GET, new HttpEntity<>(h), Map.class
                ).getBody();
                if (body != null && body.get("data") != null) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    return new QueueStats(
                            toLong(data.get("queueDepth")),
                            toLong(data.get("runningJobs")),
                            toLong(data.get("onlineWorkers")),
                            toLong(data.get("idleWorkers"))
                    );
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    private long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private record QueueStats(long queueDepth, long runningJobs, long onlineWorkers, long idleWorkers) {}
}
