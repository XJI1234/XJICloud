package com.xjicloud.frameworkintegration;

import com.xjicloud.common.ApiResponse;
import com.xjicloud.job.JobStatus;
import com.xjicloud.job.TrainingJobRepository;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.worker.WorkerNodeRepository;
import com.xjicloud.worker.WorkerStatus;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/framework")
public class FrameworkIntegrationController {

    private final RedisQueueService redisQueueService;
    private final WorkerNodeRepository workerNodeRepository;
    private final TrainingJobRepository trainingJobRepository;
    private final FrameworkClient frameworkClient;
    private final FrameworkConfigProvider configProvider;

    public FrameworkIntegrationController(
            RedisQueueService redisQueueService,
            WorkerNodeRepository workerNodeRepository,
            TrainingJobRepository trainingJobRepository,
            FrameworkClient frameworkClient,
            FrameworkConfigProvider configProvider
    ) {
        this.redisQueueService = redisQueueService;
        this.workerNodeRepository = workerNodeRepository;
        this.trainingJobRepository = trainingJobRepository;
        this.frameworkClient = frameworkClient;
        this.configProvider = configProvider;
    }

    @GetMapping("/queue-stats")
    public ApiResponse<Map<String, Object>> queueStats() {
        long onlineWorkers = workerNodeRepository.findAll().stream()
                .filter(w -> w.getStatus() != WorkerStatus.OFFLINE).count();
        long idleWorkers = workerNodeRepository.findAll().stream()
                .filter(w -> w.getStatus() == WorkerStatus.IDLE).count();
        return ApiResponse.ok(Map.of(
                "queueDepth", redisQueueService.queueDepth(),
                "runningJobs", trainingJobRepository.countByStatus(JobStatus.RUNNING),
                "onlineWorkers", onlineWorkers,
                "idleWorkers", idleWorkers,
                "lastConfigRevision", configProvider.getLastRevision()
        ));
    }

    @GetMapping("/containers/available")
    public ApiResponse<Map<String, Boolean>> containersAvailable() {
        boolean available = workerNodeRepository.findAll().stream()
                .anyMatch(w -> w.getStatus() == WorkerStatus.IDLE)
                || frameworkClient.hasAvailableContainer();
        return ApiResponse.ok(Map.of("available", available));
    }

    @PostMapping("/config/sync")
    public ApiResponse<Void> syncConfig() {
        configProvider.refresh();
        return ApiResponse.ok("配置已同步", null);
    }
}
