package com.xjicloud.framework.dashboard;

import com.xjicloud.framework.common.ApiResponse;
import com.xjicloud.framework.eci.EciInstanceRepository;
import com.xjicloud.framework.eci.EciStatus;
import com.xjicloud.framework.integration.SyncStatusRegistry;
import com.xjicloud.framework.monitor.SystemMetricsService;
import com.xjicloud.framework.node.AgentStatus;
import com.xjicloud.framework.node.ManagedNodeRepository;
import com.xjicloud.framework.configregistry.RuntimeConfigService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final SystemMetricsService metricsService;
    private final ManagedNodeRepository nodeRepository;
    private final EciInstanceRepository eciRepository;
    private final RuntimeConfigService configService;
    private final SyncStatusRegistry syncStatusRegistry;

    public DashboardController(
            SystemMetricsService metricsService,
            ManagedNodeRepository nodeRepository,
            EciInstanceRepository eciRepository,
            RuntimeConfigService configService,
            SyncStatusRegistry syncStatusRegistry
    ) {
        this.metricsService = metricsService;
        this.nodeRepository = nodeRepository;
        this.eciRepository = eciRepository;
        this.configService = configService;
        this.syncStatusRegistry = syncStatusRegistry;
    }

    @GetMapping
    public ApiResponse<DashboardView> dashboard() {
        long onlineNodes = nodeRepository.findAll().stream()
                .filter(n -> n.getAgentStatus() == AgentStatus.ONLINE).count();
        long eciRunning = eciRepository.findByStatusIn(List.of(EciStatus.RUNNING, EciStatus.PENDING)).size();
        return ApiResponse.ok(new DashboardView(
                metricsService.collectMetrics(),
                onlineNodes,
                nodeRepository.count(),
                eciRunning,
                configService.getSnapshot().revision(),
                syncStatusRegistry.all()
        ));
    }

    public record DashboardView(
            Map<String, Object> localMetrics,
            long onlineNodes,
            long totalNodes,
            long eciActive,
            int configRevision,
            Map<String, ?> syncStatus
    ) {}
}
