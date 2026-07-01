package com.xjicloud.framework.integration;

import com.xjicloud.framework.common.ApiResponse;
import com.xjicloud.framework.config.MasterOnly;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument;
import com.xjicloud.framework.configregistry.RuntimeConfigService;
import com.xjicloud.framework.configregistry.SecretCrypto;
import com.xjicloud.framework.eci.EciInstanceService;
import com.xjicloud.framework.eci.EciInstanceView;
import com.xjicloud.framework.eci.CreateContainerRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@MasterOnly
@RequestMapping("/api/v1/integration")
public class IntegrationController {

    private final RuntimeConfigService configService;
    private final EciInstanceService eciService;
    private final SyncStatusRegistry syncStatusRegistry;

    public IntegrationController(
            RuntimeConfigService configService,
            EciInstanceService eciService,
            SyncStatusRegistry syncStatusRegistry
    ) {
        this.configService = configService;
        this.eciService = eciService;
        this.syncStatusRegistry = syncStatusRegistry;
    }

    @GetMapping("/runtime-config")
    public ResponseEntity<ApiResponse<RuntimeConfigResponse>> runtimeConfig(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
            HttpServletRequest request
    ) {
        RuntimeConfigService.RuntimeConfigSnapshot snap = configService.getSnapshot();
        if (ifNoneMatch != null && ifNoneMatch.equals("revision-" + snap.revision())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        RuntimeConfigDocument doc = configService.getFullDocumentForBackend();
        return ResponseEntity.ok()
                .header("ETag", "revision-" + snap.revision())
                .body(ApiResponse.ok(new RuntimeConfigResponse(snap.revision(), snap.updatedAt(), doc)));
    }

    @PostMapping("/sync-status")
    public ApiResponse<Void> reportSyncStatus(@RequestBody SyncStatusReport report) {
        syncStatusRegistry.record("backend", report.lastAppliedRevision(), Instant.now());
        return ApiResponse.ok(null);
    }

    @GetMapping("/containers")
    public ApiResponse<List<EciInstanceView>> listContainers() {
        return ApiResponse.ok(eciService.listInstances());
    }

    @PostMapping("/containers")
    public ApiResponse<EciInstanceView> createContainer(@RequestBody CreateContainerRequest request) {
        return ApiResponse.ok(eciService.createInstance(request.name()));
    }

    @GetMapping("/containers/available")
    public ApiResponse<Map<String, Boolean>> containersAvailable() {
        return ApiResponse.ok(Map.of("available", eciService.hasAvailableCapacity()));
    }

    public record RuntimeConfigResponse(int revision, Instant updatedAt, RuntimeConfigDocument config) {}
    public record SyncStatusReport(int lastAppliedRevision) {}
}
