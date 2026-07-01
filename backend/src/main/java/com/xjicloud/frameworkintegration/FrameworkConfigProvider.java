package com.xjicloud.frameworkintegration;

import com.fasterxml.jackson.databind.JsonNode;
import com.xjicloud.oss.OssRuntimeConfig;
import com.xjicloud.oss.OssStorageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FrameworkConfigProvider {

    private static final Logger log = LoggerFactory.getLogger(FrameworkConfigProvider.class);

    private final FrameworkClient frameworkClient;
    private final FrameworkIntegrationProperties properties;
    private final OssStorageService ossStorageService;
    private volatile int lastRevision = -1;

    public FrameworkConfigProvider(
            FrameworkClient frameworkClient,
            FrameworkIntegrationProperties properties,
            OssStorageService ossStorageService
    ) {
        this.frameworkClient = frameworkClient;
        this.properties = properties;
        this.ossStorageService = ossStorageService;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${xjicloud.framework.config-poll-interval-sec:60}000")
    public void refresh() {
        if (!frameworkClient.isEnabled()) {
            return;
        }
        JsonNode data = frameworkClient.fetchRuntimeConfig(lastRevision >= 0 ? "revision-" + lastRevision : null);
        if (data == null || data.isMissingNode()) {
            return;
        }
        int revision = data.path("revision").asInt(-1);
        if (revision == lastRevision) {
            return;
        }
        JsonNode config = data.path("config");
        applyOss(config.path("oss"));
        lastRevision = revision;
        frameworkClient.reportSyncStatus(revision);
        log.info("Applied framework runtime config revision {}", revision);
    }

    private void applyOss(JsonNode oss) {
        if (oss.isMissingNode() || oss.isNull()) {
            return;
        }
        OssRuntimeConfig runtime = new OssRuntimeConfig(
                oss.path("endpoint").asText(),
                oss.path("region").asText("us-east-1"),
                oss.path("bucket").asText("xjicloud"),
                oss.path("accessKey").asText(),
                oss.path("secretKey").asText(),
                oss.path("pathStyleAccess").asBoolean(true),
                oss.path("presignExpirationMinutes").asInt(120)
        );
        ossStorageService.applyFrameworkConfig(runtime);
    }

    public int getLastRevision() {
        return lastRevision;
    }
}
