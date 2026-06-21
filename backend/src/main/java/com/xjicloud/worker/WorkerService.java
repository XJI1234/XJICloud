package com.xjicloud.worker;

import com.xjicloud.auth.JwtService;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.WorkerProperties;
import com.xjicloud.job.DatasetAsset;
import com.xjicloud.job.DatasetAssetRepository;
import com.xjicloud.job.JobStatus;
import com.xjicloud.job.TrainingJob;
import com.xjicloud.job.TrainingJobRepository;
import com.xjicloud.job.TrainingJobService;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.worker.dto.JobCompleteRequest;
import com.xjicloud.worker.dto.JobFailRequest;
import com.xjicloud.worker.dto.JobProgressRequest;
import com.xjicloud.worker.dto.WorkerHeartbeatRequest;
import com.xjicloud.worker.dto.WorkerImageItem;
import com.xjicloud.worker.dto.WorkerJobPayload;
import com.xjicloud.worker.dto.WorkerRegisterRequest;
import com.xjicloud.worker.dto.WorkerRegisterResponse;
import com.xjicloud.worker.dto.WorkerResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerService {

    private final WorkerNodeRepository workerNodeRepository;
    private final TrainingJobRepository trainingJobRepository;
    private final DatasetAssetRepository datasetAssetRepository;
    private final TrainingJobService trainingJobService;
    private final OssStorageService ossStorageService;
    private final RedisQueueService redisQueueService;
    private final JwtService jwtService;
    private final WorkerProperties workerProperties;

    public WorkerService(
            WorkerNodeRepository workerNodeRepository,
            TrainingJobRepository trainingJobRepository,
            DatasetAssetRepository datasetAssetRepository,
            TrainingJobService trainingJobService,
            OssStorageService ossStorageService,
            RedisQueueService redisQueueService,
            JwtService jwtService,
            WorkerProperties workerProperties
    ) {
        this.workerNodeRepository = workerNodeRepository;
        this.trainingJobRepository = trainingJobRepository;
        this.datasetAssetRepository = datasetAssetRepository;
        this.trainingJobService = trainingJobService;
        this.ossStorageService = ossStorageService;
        this.redisQueueService = redisQueueService;
        this.jwtService = jwtService;
        this.workerProperties = workerProperties;
    }

    @Transactional
    public WorkerRegisterResponse register(String sharedSecret, WorkerRegisterRequest request) {
        validateSharedSecret(sharedSecret);
        UUID workerId = UUID.randomUUID();
        WorkerNode worker = new WorkerNode();
        worker.setId(workerId);
        worker.setName(request.name().trim());
        worker.setGpuInfo(request.gpuInfo());
        worker.setStatus(WorkerStatus.IDLE);
        worker.setLastHeartbeat(Instant.now());
        workerNodeRepository.save(worker);

        String token = jwtService.generateWorkerToken(workerId, worker.getName());
        return new WorkerRegisterResponse(workerId, token);
    }

    @Transactional
    public void heartbeat(WorkerNode worker, WorkerHeartbeatRequest request) {
        worker.setLastHeartbeat(Instant.now());
        if (request.gpuInfo() != null && !request.gpuInfo().isBlank()) {
            worker.setGpuInfo(request.gpuInfo());
        }
        if (worker.getCurrentJobId() == null) {
            worker.setStatus(WorkerStatus.IDLE);
        }
        workerNodeRepository.save(worker);
    }

    @Transactional
    public WorkerJobPayload pollNextJob(WorkerNode worker) {
        heartbeat(worker, new WorkerHeartbeatRequest(worker.getGpuInfo()));
        if (worker.getStatus() == WorkerStatus.BUSY && worker.getCurrentJobId() != null) {
            return buildPayload(worker.getCurrentJobId());
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            UUID jobId = redisQueueService.dequeueBlocking(workerProperties.pollTimeoutSec());
            if (jobId == null) {
                return null;
            }

            TrainingJob job = trainingJobRepository.findById(jobId).orElse(null);
            if (job == null || job.getStatus() == JobStatus.CANCELLED) {
                continue;
            }

            worker.setStatus(WorkerStatus.BUSY);
            worker.setCurrentJobId(jobId);
            workerNodeRepository.save(worker);

            trainingJobService.markRunning(jobId, worker.getId());
            return buildPayload(jobId);
        }
        return null;
    }

    @Transactional
    public void reportProgress(WorkerNode worker, UUID jobId, JobProgressRequest request) {
        requireWorkerJob(worker, jobId);
        trainingJobService.updateProgress(jobId, JobStatus.RUNNING, request.percent(), request.stage(), request.message());
    }

    @Transactional
    public void completeJob(WorkerNode worker, UUID jobId, JobCompleteRequest request) {
        requireWorkerJob(worker, jobId);
        trainingJobService.markCompleted(jobId, request.outputOssKey());
        releaseWorker(worker);
    }

    @Transactional
    public void failJob(WorkerNode worker, UUID jobId, JobFailRequest request) {
        requireWorkerJob(worker, jobId);
        trainingJobService.markFailed(jobId, request.errorMessage());
        releaseWorker(worker);
    }

    public List<WorkerResponse> listWorkers() {
        return workerNodeRepository.findAllByOrderByRegisteredAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void forceOffline(UUID workerId) {
        WorkerNode worker = workerNodeRepository.findById(workerId)
                .orElseThrow(() -> new BusinessException("Worker 不存在", HttpStatus.NOT_FOUND));
        worker.setStatus(WorkerStatus.OFFLINE);
        worker.setCurrentJobId(null);
        workerNodeRepository.save(worker);
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void markStaleWorkersOffline() {
        Instant threshold = Instant.now().minus(workerProperties.heartbeatTimeoutSec(), ChronoUnit.SECONDS);
        for (WorkerNode worker : workerNodeRepository.findAll()) {
            if (worker.getLastHeartbeat().isBefore(threshold) && worker.getStatus() != WorkerStatus.OFFLINE) {
                worker.setStatus(WorkerStatus.OFFLINE);
                workerNodeRepository.save(worker);
            }
        }
    }

    private WorkerJobPayload buildPayload(UUID jobId) {
        TrainingJob job = trainingJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("任务不存在", HttpStatus.NOT_FOUND));
        List<WorkerImageItem> images = datasetAssetRepository.findByJobIdOrderByFileNameAsc(jobId).stream()
                .map(this::toImageItem)
                .toList();
        String outputKey = job.getOutputOssKey() != null
                ? job.getOutputOssKey()
                : ossStorageService.buildOutputKey(jobId);
        return new WorkerJobPayload(
                jobId,
                job.getInputOssPrefix(),
                images,
                ossStorageService.presignPutUrl(outputKey, "application/octet-stream"),
                outputKey
        );
    }

    private WorkerImageItem toImageItem(DatasetAsset asset) {
        return new WorkerImageItem(
                asset.getFileName(),
                asset.getOssKey(),
                ossStorageService.presignGetUrl(asset.getOssKey())
        );
    }

    private void requireWorkerJob(WorkerNode worker, UUID jobId) {
        if (worker.getCurrentJobId() == null || !worker.getCurrentJobId().equals(jobId)) {
            throw new BusinessException("Worker 未绑定该任务", HttpStatus.FORBIDDEN);
        }
    }

    private void releaseWorker(WorkerNode worker) {
        worker.setStatus(WorkerStatus.IDLE);
        worker.setCurrentJobId(null);
        workerNodeRepository.save(worker);
    }

    private void validateSharedSecret(String sharedSecret) {
        if (sharedSecret == null || !sharedSecret.equals(workerProperties.sharedSecret())) {
            throw new BusinessException("Worker 密钥无效", HttpStatus.UNAUTHORIZED);
        }
    }

    private WorkerResponse toResponse(WorkerNode worker) {
        return new WorkerResponse(
                worker.getId(),
                worker.getName(),
                worker.getStatus(),
                worker.getGpuInfo(),
                worker.getLastHeartbeat(),
                worker.getCurrentJobId(),
                worker.getRegisteredAt()
        );
    }
}
