package com.xjicloud.admin;

import com.xjicloud.admin.dto.DashboardResponse;
import com.xjicloud.admin.dto.OssConfigResponse;
import com.xjicloud.admin.dto.UpdateOssConfigRequest;
import com.xjicloud.auth.UserAccountRepository;
import com.xjicloud.job.JobStatus;
import com.xjicloud.job.TrainingJobRepository;
import com.xjicloud.job.TrainingJobService;
import com.xjicloud.job.dto.JobResponse;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.project.ProjectRepository;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.worker.WorkerNodeRepository;
import com.xjicloud.worker.WorkerService;
import com.xjicloud.worker.WorkerStatus;
import com.xjicloud.worker.dto.WorkerResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private final WorkerNodeRepository workerNodeRepository;
    private final WorkerService workerService;
    private final RedisQueueService redisQueueService;
    private final TrainingJobRepository trainingJobRepository;
    private final TrainingJobService trainingJobService;
    private final OssStorageService ossStorageService;
    private final UserAccountRepository userAccountRepository;
    private final ProjectRepository projectRepository;

    public AdminService(
            WorkerNodeRepository workerNodeRepository,
            WorkerService workerService,
            RedisQueueService redisQueueService,
            TrainingJobRepository trainingJobRepository,
            TrainingJobService trainingJobService,
            OssStorageService ossStorageService,
            UserAccountRepository userAccountRepository,
            ProjectRepository projectRepository
    ) {
        this.workerNodeRepository = workerNodeRepository;
        this.workerService = workerService;
        this.redisQueueService = redisQueueService;
        this.trainingJobRepository = trainingJobRepository;
        this.trainingJobService = trainingJobService;
        this.ossStorageService = ossStorageService;
        this.userAccountRepository = userAccountRepository;
        this.projectRepository = projectRepository;
    }

    public DashboardResponse getDashboard() {
        Instant todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        long onlineWorkers = workerNodeRepository.findAll().stream()
                .filter(worker -> worker.getStatus() != WorkerStatus.OFFLINE)
                .count();
        long completedToday = trainingJobRepository.findAll().stream()
                .filter(job -> job.getStatus() == JobStatus.COMPLETED && job.getUpdatedAt().isAfter(todayStart))
                .count();
        return new DashboardResponse(
                workerNodeRepository.count(),
                onlineWorkers,
                redisQueueService.queueDepth(),
                trainingJobRepository.countByStatus(JobStatus.RUNNING),
                completedToday,
                trainingJobRepository.countByStatus(JobStatus.FAILED)
        );
    }

    public OssConfigResponse getOssConfig() {
        return new OssConfigResponse(ossStorageService.getPublicConfig());
    }

    public OssConfigResponse updateOssConfig(UpdateOssConfigRequest request, String updatedBy) {
        ossStorageService.updateConfig(request.config(), updatedBy);
        return new OssConfigResponse(ossStorageService.getPublicConfig());
    }

    public void testOssConnection() {
        ossStorageService.testConnection();
    }

    public List<WorkerResponse> listWorkers() {
        return workerService.listWorkers();
    }

    public void forceOfflineWorker(UUID workerId) {
        workerService.forceOffline(workerId);
    }

    public List<JobResponse> listJobs() {
        return trainingJobService.listAllJobs();
    }

    public JobResponse retryJob(UUID jobId) {
        return trainingJobService.retryJob(jobId);
    }

    public JobResponse cancelJob(UUID jobId) {
        return trainingJobService.cancelJob(jobId);
    }

    public long countUsers() {
        return userAccountRepository.count();
    }

    public long countProjects() {
        return projectRepository.count();
    }
}
