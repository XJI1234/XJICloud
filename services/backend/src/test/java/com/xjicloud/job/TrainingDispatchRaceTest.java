package com.xjicloud.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xjicloud.admin.EciManagerClient;
import com.xjicloud.admin.EciManagerTaskRequest;
import com.xjicloud.admin.EciManagerTaskResponse;
import com.xjicloud.config.EciManagerProperties;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.project.ProjectService;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.sse.JobProgressSseService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest
@Import({TrainingDispatchService.class, TrainingJobService.class})
@EnableConfigurationProperties(EciManagerProperties.class)
@TestPropertySource(properties = {
        "xjicloud.eci-manager.base-url=http://127.0.0.1:1",
        "xjicloud.eci-manager.api-key=test-eci-key",
        "xjicloud.eci-manager.training-image=registry.example.test/xjicloud/worker:test"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TrainingDispatchRaceTest {

    @Autowired
    private TrainingJobRepository jobs;

    @Autowired
    private DatasetAssetRepository assets;

    @Autowired
    private TrainingDispatchService dispatchService;

    @Autowired
    private TrainingJobService jobService;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private RedisQueueService queueService;

    @MockBean
    private EciManagerClient eciManagerClient;

    @MockBean
    private OssStorageService ossStorageService;

    @MockBean
    private JobProgressSseService sseService;

    @BeforeEach
    void clearRows() {
        assets.deleteAll();
        jobs.deleteAll();
    }

    @Test
    void expiredClaimTakeoverWithTheSameAttemptDoesNotCancelTheIdempotentTask() {
        UUID jobId = queuedJob(1, "stable-attempt-key");
        UUID taskId = UUID.randomUUID();
        AtomicInteger postCount = new AtomicInteger();
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId, jobId);
        when(eciManagerClient.createTask(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (postCount.incrementAndGet() == 1) {
                TrainingJob expiredClaim = jobs.findById(jobId).orElseThrow();
                expiredClaim.setDispatchClaimedAt(Instant.now().minus(Duration.ofMinutes(5)));
                jobs.saveAndFlush(expiredClaim);
                dispatchService.dispatchNext();
            }
            return new EciManagerTaskResponse(taskId, "STARTING");
        });

        dispatchService.dispatchNext();

        TrainingJob persisted = jobs.findById(jobId).orElseThrow();
        assertThat(persisted.getEciTaskId()).isEqualTo(taskId);
        assertThat(persisted.getDispatchAttempt()).isEqualTo(1);
        assertThat(persisted.getClientRequestId()).isEqualTo("stable-attempt-key");
        verify(eciManagerClient, times(2)).createTask(any(EciManagerTaskRequest.class));
        verify(eciManagerClient, never()).cancelTask(taskId);
    }

    @Test
    void missingConfigurationDoesNotCreateCancellationPendingWithoutAClaimedPost() {
        UUID jobId = queuedJob(1, "not-yet-posted-key");
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId);
        TrainingDispatchService unconfigured = serviceWithProperties(new EciManagerProperties(
                "http://127.0.0.1:1", "test-eci-key", Duration.ofMillis(125), Duration.ofMillis(750), ""));

        unconfigured.dispatchNext();

        TrainingJob deferred = jobs.findById(jobId).orElseThrow();
        assertThat(deferred.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(deferred.getDispatchClaimedAt()).isNull();
        assertThat(deferred.isEciCancellationPending()).isFalse();

        jobService.cancelJob(jobId);

        TrainingJob cancelled = jobs.findById(jobId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(cancelled.isEciCancellationPending()).isFalse();
        verify(eciManagerClient, never()).createTask(any());
    }

    @Test
    void missingConfigurationPreservesCancellationPendingForAnAmbiguousPost() {
        UUID jobId = queuedJob(2, "accepted-before-cancel-key");
        TrainingJob cancelled = jobs.findById(jobId).orElseThrow();
        cancelled.setStatus(JobStatus.CANCELLED);
        cancelled.setEciCancellationPending(true);
        cancelled.setDispatchClaimedAt(Instant.now().minus(Duration.ofMinutes(5)));
        jobs.saveAndFlush(cancelled);
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId);
        TrainingDispatchService unconfigured = serviceWithProperties(new EciManagerProperties(
                "http://127.0.0.1:1", " ", Duration.ofMillis(125), Duration.ofMillis(750), "worker:test"));

        unconfigured.dispatchNext();

        TrainingJob deferred = jobs.findById(jobId).orElseThrow();
        assertThat(deferred.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(deferred.getClientRequestId()).isEqualTo("accepted-before-cancel-key");
        assertThat(deferred.isEciCancellationPending()).isTrue();
        assertThat(deferred.getDispatchNextAttemptAt()).isAfter(Instant.now());
        unconfigured.recoverPendingJobs();
        verify(queueService, never()).enqueue(jobId);

        deferred.setDispatchNextAttemptAt(Instant.now().minusSeconds(1));
        jobs.saveAndFlush(deferred);
        unconfigured.recoverPendingJobs();

        verify(queueService).enqueue(jobId);
        verify(eciManagerClient, never()).createTask(any());
    }

    private UUID queuedJob(int attempt, String clientRequestId) {
        UUID jobId = UUID.randomUUID();
        TrainingJob job = new TrainingJob();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());
        job.setUserId(UUID.randomUUID());
        job.setName("dispatch-race-test");
        job.setStatus(JobStatus.QUEUED);
        job.setStage("queued");
        job.setInputOssPrefix("datasets/" + jobId + "/");
        job.setOutputOssKey("outputs/" + jobId + "/model.ply");
        job.setDispatchAttempt(attempt);
        job.setClientRequestId(clientRequestId);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jobs.saveAndFlush(job);
        return jobId;
    }

    private TrainingDispatchService serviceWithProperties(EciManagerProperties properties) {
        return new TrainingDispatchService(
                jobs,
                assets,
                queueService,
                eciManagerClient,
                ossStorageService,
                properties,
                sseService
        );
    }
}
