package com.xjicloud.job;

import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.BusinessException;
import com.xjicloud.job.dto.CreateDatasetRequest;
import com.xjicloud.job.dto.CreateDatasetResponse;
import com.xjicloud.job.dto.DatasetFileRequest;
import com.xjicloud.job.dto.JobResponse;
import com.xjicloud.job.dto.PresignedUploadItem;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.project.ProjectService;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.sse.JobProgressSseService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingJobService {

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private final TrainingJobRepository trainingJobRepository;
    private final DatasetAssetRepository datasetAssetRepository;
    private final ProjectService projectService;
    private final OssStorageService ossStorageService;
    private final RedisQueueService redisQueueService;
    private final JobProgressSseService jobProgressSseService;

    public TrainingJobService(
            TrainingJobRepository trainingJobRepository,
            DatasetAssetRepository datasetAssetRepository,
            ProjectService projectService,
            OssStorageService ossStorageService,
            RedisQueueService redisQueueService,
            JobProgressSseService jobProgressSseService
    ) {
        this.trainingJobRepository = trainingJobRepository;
        this.datasetAssetRepository = datasetAssetRepository;
        this.projectService = projectService;
        this.ossStorageService = ossStorageService;
        this.redisQueueService = redisQueueService;
        this.jobProgressSseService = jobProgressSseService;
    }

    @Transactional
    public CreateDatasetResponse createDataset(UserAccount user, UUID projectId, CreateDatasetRequest request) {
        projectService.requireOwnedProject(user, projectId);
        validateFiles(request.files());

        UUID jobId = UUID.randomUUID();
        String inputPrefix = ossStorageService.buildDatasetPrefix(jobId);

        TrainingJob job = new TrainingJob();
        job.setId(jobId);
        job.setProjectId(projectId);
        job.setUserId(user.getId());
        job.setName(request.name().trim());
        job.setStatus(JobStatus.UPLOADING);
        job.setStage("upload");
        job.setMessage("等待图片上传");
        job.setInputOssPrefix(inputPrefix);
        job.setOutputOssKey(ossStorageService.buildOutputKey(jobId));
        trainingJobRepository.save(job);

        List<PresignedUploadItem> uploads = new ArrayList<>();
        for (DatasetFileRequest file : request.files()) {
            String ossKey = ossStorageService.buildDatasetImageKey(jobId, file.archivedName());
            String uploadUrl = ossStorageService.presignPutUrl(ossKey, file.contentType());
            uploads.add(new PresignedUploadItem(file.archivedName(), ossKey, uploadUrl, file.contentType()));

            DatasetAsset asset = new DatasetAsset();
            asset.setJobId(jobId);
            asset.setFileName(file.originalName());
            asset.setOssKey(ossKey);
            asset.setSizeBytes(file.sizeBytes());
            asset.setContentType(file.contentType());
            datasetAssetRepository.save(asset);
        }

        String manifestKey = inputPrefix + "manifest.json";
        String manifestUploadUrl = ossStorageService.presignPutUrl(manifestKey, "application/json");
        uploads.add(new PresignedUploadItem("manifest.json", manifestKey, manifestUploadUrl, "application/json"));

        return new CreateDatasetResponse(jobId, manifestUploadUrl, uploads);
    }

    @Transactional
    public JobResponse completeDataset(UserAccount user, UUID projectId, UUID jobId) {
        TrainingJob job = requireOwnedJob(user, projectId, jobId);
        if (job.getStatus() != JobStatus.UPLOADING) {
            throw new BusinessException("任务状态不允许完成上传");
        }

        job.setStatus(JobStatus.QUEUED);
        job.setProgress(0);
        job.setStage("queued");
        job.setMessage("已入队，等待算力容器");
        job.setUpdatedAt(Instant.now());
        trainingJobRepository.save(job);

        redisQueueService.enqueue(jobId);
        publishProgress(job);
        return toResponse(job);
    }

    public List<JobResponse> listProjectJobs(UserAccount user, UUID projectId) {
        projectService.requireOwnedProject(user, projectId);
        return trainingJobRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    public JobResponse getJob(UserAccount user, UUID jobId) {
        TrainingJob job = requireOwnedJob(user, jobId);
        return toResponse(job);
    }

    public TrainingJob requireJob(UUID jobId) {
        return trainingJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("任务不存在", HttpStatus.NOT_FOUND));
    }

    public TrainingJob requireOwnedJob(UserAccount user, UUID jobId) {
        TrainingJob job = requireJob(jobId);
        if (!job.getUserId().equals(user.getId())) {
            throw new BusinessException("无权访问该任务", HttpStatus.FORBIDDEN);
        }
        projectService.requireOwnedProject(user, job.getProjectId());
        return job;
    }

    private TrainingJob requireOwnedJob(UserAccount user, UUID projectId, UUID jobId) {
        TrainingJob job = requireOwnedJob(user, jobId);
        if (!job.getProjectId().equals(projectId)) {
            throw new BusinessException("任务不属于该项目", HttpStatus.BAD_REQUEST);
        }
        return job;
    }

    @Transactional
    public void updateProgress(UUID jobId, JobStatus status, int progress, String stage, String message) {
        TrainingJob job = requireJob(jobId);
        job.setStatus(status);
        job.setProgress(progress);
        job.setStage(stage);
        job.setMessage(message);
        job.setUpdatedAt(Instant.now());
        trainingJobRepository.save(job);
        publishProgress(job);
    }

    @Transactional
    public void markRunning(UUID jobId, UUID workerId) {
        TrainingJob job = requireJob(jobId);
        job.setStatus(JobStatus.RUNNING);
        job.setWorkerId(workerId);
        job.setStage("training");
        job.setMessage("算力容器已开始训练");
        job.setUpdatedAt(Instant.now());
        trainingJobRepository.save(job);
        publishProgress(job);
    }

    @Transactional
    public JobResponse markCompleted(UUID jobId, String outputOssKey) {
        TrainingJob job = requireJob(jobId);
        job.setStatus(JobStatus.COMPLETED);
        job.setProgress(100);
        job.setStage("completed");
        job.setMessage("训练完成");
        job.setOutputOssKey(outputOssKey);
        job.setOutputDownloadUrl(ossStorageService.presignGetUrl(outputOssKey));
        job.setUpdatedAt(Instant.now());
        trainingJobRepository.save(job);
        publishProgress(job);
        return toResponse(job);
    }

    @Transactional
    public JobResponse markFailed(UUID jobId, String errorMessage) {
        TrainingJob job = requireJob(jobId);
        job.setStatus(JobStatus.FAILED);
        job.setStage("failed");
        job.setMessage("训练失败");
        job.setErrorMessage(errorMessage);
        job.setUpdatedAt(Instant.now());
        trainingJobRepository.save(job);
        publishProgress(job);
        return toResponse(job);
    }

    @Transactional
    public JobResponse retryJob(UUID jobId) {
        TrainingJob job = requireJob(jobId);
        if (job.getStatus() != JobStatus.FAILED && job.getStatus() != JobStatus.CANCELLED) {
            throw new BusinessException("仅失败或已取消的任务可重试");
        }
        job.setStatus(JobStatus.QUEUED);
        job.setProgress(0);
        job.setStage("queued");
        job.setMessage("已重新入队");
        job.setErrorMessage(null);
        job.setWorkerId(null);
        job.setUpdatedAt(Instant.now());
        trainingJobRepository.save(job);
        redisQueueService.enqueue(jobId);
        publishProgress(job);
        return toResponse(job);
    }

    @Transactional
    public JobResponse cancelJob(UUID jobId) {
        TrainingJob job = requireJob(jobId);
        if (job.getStatus() == JobStatus.COMPLETED) {
            throw new BusinessException("已完成的任务无法取消");
        }
        job.setStatus(JobStatus.CANCELLED);
        job.setStage("cancelled");
        job.setMessage("任务已取消");
        job.setUpdatedAt(Instant.now());
        trainingJobRepository.save(job);
        redisQueueService.remove(jobId);
        publishProgress(job);
        return toResponse(job);
    }

    public List<JobResponse> listAllJobs() {
        return trainingJobRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private void publishProgress(TrainingJob job) {
        jobProgressSseService.publish(
                job.getId(),
                job.getStatus(),
                job.getProgress(),
                job.getStage(),
                job.getMessage()
        );
    }

    private JobResponse toResponse(TrainingJob job) {
        String downloadUrl = job.getOutputDownloadUrl();
        if (downloadUrl == null && job.getOutputOssKey() != null && job.getStatus() == JobStatus.COMPLETED) {
            downloadUrl = ossStorageService.presignGetUrl(job.getOutputOssKey());
        }
        return new JobResponse(
                job.getId(),
                job.getProjectId(),
                job.getName(),
                job.getStatus(),
                job.getProgress(),
                job.getStage(),
                job.getMessage(),
                downloadUrl,
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private void validateFiles(List<DatasetFileRequest> files) {
        for (DatasetFileRequest file : files) {
            String contentType = file.contentType().toLowerCase(Locale.ROOT);
            if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
                throw new BusinessException("不支持的图片类型: " + file.contentType());
            }
            String lowerName = file.archivedName().toLowerCase(Locale.ROOT);
            if (!lowerName.matches("\\d{4}\\.(jpg|jpeg|png|webp)")) {
                throw new BusinessException("归档文件名格式无效: " + file.archivedName());
            }
        }
    }
}
