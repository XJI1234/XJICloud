package com.xjicloud.job;
import com.xjicloud.admin.EciManagerClient;
import com.xjicloud.admin.EciManagerTaskRequest;
import com.xjicloud.admin.EciManagerTaskResponse;
import com.xjicloud.config.EciManagerProperties;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.sse.JobProgressSseService;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Service
public class TrainingDispatchService {
    private static final Logger log = LoggerFactory.getLogger(TrainingDispatchService.class);
    private static final long QUEUE_WAIT_SECONDS = 1;
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(2);
    private static final Duration RECOVERY_INTERVAL = Duration.ofSeconds(30);
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(30);
    private static final List<JobStatus> BOUND = List.of(JobStatus.RUNNING, JobStatus.CANCELLED);
    private final TrainingJobRepository jobs;
    private final DatasetAssetRepository assets;
    private final RedisQueueService queue;
    private final EciManagerClient eci;
    private final OssStorageService oss;
    private final EciManagerProperties properties;
    private final JobProgressSseService sse;
    private final ResourceLoader resources;
    private final TransactionTemplate tx;
    @Autowired
    public TrainingDispatchService(TrainingJobRepository jobs, DatasetAssetRepository assets,
            RedisQueueService queue, EciManagerClient eci, OssStorageService oss,
            EciManagerProperties properties, JobProgressSseService sse,
            Map<String, ResourceLoader> resourceLoaders, PlatformTransactionManager transactions) {
        this(jobs, assets, queue, eci, oss, properties, sse,
                resourceLoaders.values().stream().filter(r -> !(r instanceof ApplicationContext)).findFirst()
                        .orElseGet(DefaultResourceLoader::new), new TransactionTemplate(transactions));
    }
    public TrainingDispatchService(TrainingJobRepository jobs, DatasetAssetRepository assets,
            RedisQueueService queue, EciManagerClient eci, OssStorageService oss,
            EciManagerProperties properties, JobProgressSseService sse) {
        this(jobs, assets, queue, eci, oss, properties, sse, new DefaultResourceLoader(), null);
    }
    private TrainingDispatchService(TrainingJobRepository jobs, DatasetAssetRepository assets,
            RedisQueueService queue, EciManagerClient eci, OssStorageService oss, EciManagerProperties properties,
            JobProgressSseService sse, ResourceLoader resources, TransactionTemplate tx) {
        this.jobs = jobs; this.assets = assets; this.queue = queue; this.eci = eci; this.oss = oss;
        this.properties = properties; this.sse = sse; this.resources = resources; this.tx = tx;
    }
    public void dispatchNext() {
        UUID id = queue.dequeueBlocking(QUEUE_WAIT_SECONDS);
        if (id == null) return;
        if (!properties.hasApiKey() || properties.trainingImage().isBlank()) {
            transact(() -> {
                TrainingJob job = locked(id);
                Instant now = Instant.now();
                if (dispatchable(job) && !blocked(job, now)) {
                    job.setDispatchEnqueuedAt(null);
                    job.setDispatchNextAttemptAt(now.plus(RETRY_BACKOFF));
                    job.setMessage(job.getStatus() == JobStatus.CANCELLED
                            ? "等待 ECI 配置以完成取消收敛" : "派发配置缺失，任务等待重试");
                    save(job, now);
                }
                return null;
            });
            return;
        }
        DispatchClaim claim = transact(() -> {
            TrainingJob job = locked(id);
            Instant now = Instant.now();
            if (!dispatchable(job) || blocked(job, now)) return null;
            Instant unresolvedRequestAt = !job.isDispatchClaimActive() ? job.getDispatchClaimedAt() : null;
            if (job.getDispatchAttempt() < 1) job.setDispatchAttempt(1);
            if (job.getClientRequestId() == null || job.getClientRequestId().isBlank()) {
                job.setClientRequestId(UUID.randomUUID().toString());
            }
            String output = job.getOutputOssKey();
            if (output == null || output.isBlank()) {
                output = oss.buildOutputKey(id);
                job.setOutputOssKey(output);
            }
            UUID token = UUID.randomUUID();
            job.setDispatchClaimActive(true); job.setDispatchClaimToken(token); job.setDispatchClaimedAt(now);
            job.setDispatchNextAttemptAt(null);
            save(job, now);
            List<String> inputs = assets.findByJobIdOrderByFileNameAsc(id).stream().map(DatasetAsset::getOssKey).toList();
            return new DispatchClaim(id, job.getDispatchAttempt(), job.getClientRequestId(), token, inputs, output,
                    unresolvedRequestAt);
        });
        if (claim == null) return;
        List<String> args;
        try {
            Resource resource = resources.getResource("classpath:3dgs-bootstrap.py");
            if (!resource.exists() || !resource.isReadable()) throw new IllegalStateException("3DGS bootstrap resource is unavailable");
            try (var input = resource.getInputStream()) {
                String encoded = Base64.getEncoder().encodeToString(input.readAllBytes());
                args = List.of("shell", "python3 -c 'import base64; exec(base64.b64decode(\"" + encoded + "\").decode())'");
            }
        } catch (IOException | RuntimeException ex) {
            log.warn("Unable to load 3DGS bootstrap for training job {} ({})", id,
                    ex.getClass().getSimpleName());
            retry(claim, false);
            return;
        }
        EciManagerTaskResponse response;
        try {
            response = eci.createTask(new EciManagerTaskRequest("training-" + id.toString().substring(0, 8) + "-" + claim.attempt(),
                    properties.trainingImage().trim(), List.of("/opt/3dgs/docker/entrypoint.sh"), args, 16.0, 60.0, 1,
                    "ecs.gn7i-c16g1.4xlarge", null, "8Gi", "Never", 21600,
                    Map.of("ROOT_DIR", "/data", "BUILDING", "job-" + id, "MAX_STEPS", "15000", "DATA_FACTOR", "1",
                            "PACKED", "1", "ANTIALIASED", "1", "MAX_LONG_EDGE", "2048", "EXPECTED_DRIVER_MAJOR", "550"),
                    claim.clientRequestId(), new EciManagerTaskRequest.Storage("keys", claim.inputKeys(), claim.outputOssKey())));
        } catch (RuntimeException ex) {
            log.warn("ECI dispatch failed for training job {} ({})", id, ex.getClass().getSimpleName());
            retry(claim, true);
            return;
        }
        if (response == null || response.id() == null || response.status() == null) { retry(claim, true); return; }
        DispatchResult result = transact(() -> {
            TrainingJob job = locked(claim.jobId());
            if (job == null || job.getDispatchAttempt() != claim.attempt()
                    || !claim.clientRequestId().equals(job.getClientRequestId())) return new DispatchResult(true, 0);
            if (job.getEciTaskId() != null) {
                return job.getEciTaskId().equals(response.id())
                        ? null : new DispatchResult(true, 0);
            }
            if (!job.isDispatchClaimActive() || !claim.claimToken().equals(job.getDispatchClaimToken())
                    || !dispatchable(job)) return null;
            job.setEciTaskId(response.id()); job.setDispatchClaimActive(false); job.setDispatchClaimToken(null); job.setDispatchClaimedAt(null);
            if (job.getStatus() == JobStatus.CANCELLED || job.isEciCancellationPending()) {
                job.setEciCancellationPending(true);
                job.setStage("cancelled");
                job.setMessage("正在取消 ECI 任务");
            } else {
                job.setStatus(JobStatus.RUNNING);
                job.setStage("training");
                job.setMessage("算力容器已开始训练");
            }
            job.setDispatchEnqueuedAt(null); job.setDispatchNextAttemptAt(null);
            save(job, Instant.now());
            return job.getStatus() == JobStatus.RUNNING ? new DispatchResult(false, job.getProgress()) : null;
        });
        if (result != null && result.stale()) {
            try {
                eci.cancelTask(response.id());
            } catch (RuntimeException ex) {
                log.warn("Unable to cancel stale ECI task {} ({})", response.id(), ex.getClass().getSimpleName());
            }
        } else if (result != null) {
            sse.publish(id, JobStatus.RUNNING, result.progress(), "training", "算力容器已开始训练");
        }
    }
    public void recoverPendingJobs() {
        List<TrainingJob> candidates = new ArrayList<>(jobs.findByStatusIn(List.of(JobStatus.QUEUED))); candidates.addAll(jobs.findByEciCancellationPendingTrueAndEciTaskIdIsNull());
        for (TrainingJob job : candidates) {
            UUID id = job.getId();
            boolean enqueue = transact(() -> {
                TrainingJob current = locked(id);
                Instant now = Instant.now();
                if (!dispatchable(current)) return false;
                if (current.isDispatchClaimActive()) {
                    if (current.getDispatchClaimedAt() != null && current.getDispatchClaimedAt().isAfter(now.minus(CLAIM_LEASE))) return false;
                    current.setDispatchClaimActive(false); current.setDispatchClaimToken(null); current.setDispatchClaimedAt(null);
                }
                if (current.getDispatchNextAttemptAt() != null && current.getDispatchNextAttemptAt().isAfter(now)) {
                    save(current, now); return false;
                }
                if (current.getDispatchAttempt() < 1) current.setDispatchAttempt(1);
                if (current.getClientRequestId() == null || current.getClientRequestId().isBlank()) {
                    current.setClientRequestId(UUID.randomUUID().toString());
                }
                if (current.getDispatchEnqueuedAt() != null && current.getDispatchEnqueuedAt().isAfter(now.minus(RECOVERY_INTERVAL))) {
                    save(current, now); return false;
                }
                current.setDispatchEnqueuedAt(now);
                save(current, now);
                return true;
            });
            if (enqueue) queue.enqueue(id);
        }
    }
    public void pollBoundJobs() {
        for (TrainingJob snapshot : jobs.findByEciTaskIdNotNullAndStatusIn(BOUND)) {
            UUID taskId = snapshot.getEciTaskId();
            if (snapshot.getStatus() == JobStatus.CANCELLED && snapshot.isEciCancellationPending()) {
                EciManagerTaskResponse response;
                try {
                    response = eci.cancelTask(taskId);
                } catch (RuntimeException ex) {
                    try {
                        response = eci.getTask(taskId);
                    } catch (RuntimeException readFailure) {
                        log.warn("Unable to confirm cancellation for ECI task {} (cancel={}, get={})", taskId,
                                ex.getClass().getSimpleName(), readFailure.getClass().getSimpleName()); continue;
                    }
                }
                if (response == null || !taskId.equals(response.id()) || !terminal(response.status())) continue;
                transact(() -> {
                    TrainingJob job = locked(snapshot.getId());
                    if (job != null && taskId.equals(job.getEciTaskId())
                            && job.getStatus() == JobStatus.CANCELLED && job.isEciCancellationPending()) {
                        job.setEciCancellationPending(false);
                        save(job, Instant.now());
                    }
                    return null;
                });
                continue;
            }
            if (snapshot.getStatus() != JobStatus.RUNNING) continue;
            EciManagerTaskResponse response;
            try {
                response = eci.getTask(taskId);
            } catch (RuntimeException ex) {
                log.warn("ECI status poll failed for training job {} ({})", snapshot.getId(), ex.getClass().getSimpleName());
                continue;
            }
            if (response == null || response.id() == null || !taskId.equals(response.id()) || !terminal(response.status())) continue;
            Projection projection = transact(() -> {
                TrainingJob job = locked(snapshot.getId());
                if (job == null || job.getStatus() != JobStatus.RUNNING || !taskId.equals(job.getEciTaskId())) return null;
                JobStatus status = switch (response.status().toUpperCase()) {
                    case "SUCCEEDED" -> JobStatus.COMPLETED;
                    case "FAILED" -> JobStatus.FAILED;
                    case "CANCELLED" -> JobStatus.CANCELLED;
                    default -> null;
                };
                job.setStatus(status);
                job.setStage(status == JobStatus.COMPLETED ? "completed" : status == JobStatus.CANCELLED ? "cancelled" : "failed");
                job.setMessage(status == JobStatus.COMPLETED ? "训练完成" : status == JobStatus.CANCELLED ? "任务已取消" : "训练失败");
                if (status == JobStatus.COMPLETED) {
                    job.setProgress(100);
                    if (job.getOutputOssKey() != null) job.setOutputDownloadUrl(oss.presignGetUrl(job.getOutputOssKey()));
                } else if (status == JobStatus.FAILED) job.setErrorMessage("ECI 训练任务失败");
                save(job, Instant.now());
                return new Projection(job);
            });
            if (projection != null) {
                sse.publish(projection.id(), projection.status(), projection.progress(),
                        projection.stage(), projection.message());
            }
        }
    }
    private void retry(DispatchClaim claim, boolean requestMayHaveBeenAccepted) {
        transact(() -> {
            TrainingJob job = locked(claim.jobId());
            if (job == null || job.getDispatchAttempt() != claim.attempt()
                    || !claim.clientRequestId().equals(job.getClientRequestId()) || !job.isDispatchClaimActive()
                    || !claim.claimToken().equals(job.getDispatchClaimToken())) return null;
            Instant now = Instant.now();
            job.setDispatchClaimActive(false); job.setDispatchClaimToken(null);
            if (!requestMayHaveBeenAccepted) {
                job.setDispatchClaimedAt(claim.unresolvedRequestAt());
                if (claim.unresolvedRequestAt() == null) job.setEciCancellationPending(false);
            }
            job.setDispatchEnqueuedAt(null);
            boolean retry = dispatchable(job);
            job.setDispatchNextAttemptAt(retry ? now.plus(RETRY_BACKOFF) : null);
            if (retry) job.setMessage(job.getStatus() == JobStatus.CANCELLED
                    ? "正在确认并取消 ECI 任务" : "派发暂不可用，稍后重试");
            save(job, now);
            return null;
        });
    }
    private boolean dispatchable(TrainingJob job) {
        return job != null && job.getEciTaskId() == null && ((job.getStatus() == JobStatus.QUEUED && !job.isEciCancellationPending())
                || (job.getStatus() == JobStatus.CANCELLED && job.isEciCancellationPending()));
    }
    private boolean blocked(TrainingJob job, Instant now) {
        return (job.getDispatchNextAttemptAt() != null && job.getDispatchNextAttemptAt().isAfter(now))
                || (job.isDispatchClaimActive() && job.getDispatchClaimedAt() != null && job.getDispatchClaimedAt().isAfter(now.minus(CLAIM_LEASE)));
    }
    private boolean terminal(String status) { return status != null && switch (status.toUpperCase()) { case "SUCCEEDED", "FAILED", "CANCELLED" -> true; default -> false; }; }
    private TrainingJob locked(UUID id) { return tx == null ? jobs.findById(id).orElse(null) : jobs.findForUpdateById(id).orElse(null); }
    private void save(TrainingJob job, Instant now) { job.setUpdatedAt(now); jobs.save(job); }
    private <T> T transact(Supplier<T> action) { return tx == null ? action.get() : tx.execute(status -> action.get()); }
    private record DispatchResult(boolean stale, int progress) { }
    private record Projection(UUID id, JobStatus status, int progress, String stage, String message) { Projection(TrainingJob job) { this(job.getId(), job.getStatus(), job.getProgress(), job.getStage(), job.getMessage()); } }
    private record DispatchClaim(UUID jobId, int attempt, String clientRequestId, UUID claimToken,
            List<String> inputKeys, String outputOssKey, Instant unresolvedRequestAt) { }
}
