package com.xjicloud.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.admin.EciManagerClient;
import com.xjicloud.admin.EciManagerTaskRequest;
import com.xjicloud.admin.EciManagerTaskResponse;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.EciManagerProperties;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.project.ProjectService;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.sse.JobProgressSseService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;
import org.mockito.ArgumentCaptor;

@DataJpaTest
@Import({TrainingDispatchService.class, TrainingJobService.class})
@EnableConfigurationProperties(EciManagerProperties.class)
@TestPropertySource(properties = {
        "xjicloud.eci-manager.base-url=http://127.0.0.1:1",
        "xjicloud.eci-manager.api-key=test-eci-key",
        "xjicloud.eci-manager.training-image=registry.example.test/xjicloud/worker:test",
        "xjicloud.eci-manager.connect-timeout=125ms",
        "xjicloud.eci-manager.read-timeout=750ms"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TrainingDispatchServiceTest {

    @Autowired
    private TrainingJobRepository jobs;

    @Autowired
    private DatasetAssetRepository assets;

    @Autowired
    private TrainingDispatchService dispatchService;

    @Autowired
    private TrainingJobService jobService;

    @Autowired
    private EciManagerProperties eciProperties;

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

    @MockBean
    private ResourceLoader resourceLoader;

    @BeforeEach
    void clearRows() {
        assets.deleteAll();
        jobs.deleteAll();
        when(resourceLoader.getResource("classpath:3dgs-bootstrap.py"))
                .thenReturn(new ClassPathResource("3dgs-bootstrap.py"));
    }

    @Test
    void dispatchBindsOneEciTaskWithStoredDatasetKeysAndOneShotCommandOutsideDatabaseTransaction()
            throws IOException {
        UUID jobId = queuedJob(1, "request-key-1");
        saveAsset(jobId, "0001.jpg", "datasets/" + jobId + "/images/0001.jpg");
        saveAsset(jobId, "0002.jpg", "datasets/" + jobId + "/images/0002.jpg");
        UUID taskId = UUID.randomUUID();
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId).thenReturn(jobId);
        when(ossStorageService.buildOutputKey(jobId)).thenReturn("outputs/" + jobId + "/model.ply");
        when(eciManagerClient.createTask(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new EciManagerTaskResponse(taskId, "STARTING");
        });

        dispatchService.dispatchNext();
        dispatchService.dispatchNext();

        TrainingJob persisted = jobs.findById(jobId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(persisted.getEciTaskId()).isEqualTo(taskId);
        assertThat(persisted.getOutputOssKey()).isEqualTo("outputs/" + jobId + "/model.ply");
        assertThat(persisted.getClientRequestId()).isEqualTo("request-key-1");
        assertThat(eciProperties.trainingImage()).isEqualTo("registry.example.test/xjicloud/worker:test");
        assertThat(eciProperties.connectTimeout()).isEqualTo(Duration.ofMillis(125));
        assertThat(eciProperties.readTimeout()).isEqualTo(Duration.ofMillis(750));
        ArgumentCaptor<EciManagerTaskRequest> request = ArgumentCaptor.forClass(EciManagerTaskRequest.class);
        verify(eciManagerClient).createTask(request.capture());
        assertThat(request.getValue().clientRequestId()).isEqualTo("request-key-1");
        assertThat(request.getValue().image()).isEqualTo("registry.example.test/xjicloud/worker:test");
        assertThat(request.getValue().command()).containsExactly("/opt/3dgs/docker/entrypoint.sh");
        assertBootstrapInvocation(request.getValue().args());
        JsonNode task = new ObjectMapper().valueToTree(request.getValue());
        assertThat(task.path("cpu").asInt()).isEqualTo(16);
        assertThat(task.path("memory").asInt()).isEqualTo(60);
        assertThat(task.path("gpu").asInt()).isEqualTo(1);
        assertThat(task.path("instanceType").asText()).isEqualTo("ecs.gn7i-c16g1.4xlarge");
        assertThat(task.path("shmSize").asText()).isEqualTo("8Gi");
        assertThat(task.path("restartPolicy").asText()).isEqualTo("Never");
        assertThat(task.path("maxRuntimeSec").asInt()).isEqualTo(21600);
        JsonNode env = task.path("env");
        assertThat(env.path("ROOT_DIR").asText()).isEqualTo("/data");
        assertThat(env.path("BUILDING").asText()).isEqualTo("job-" + jobId);
        assertThat(env.path("MAX_STEPS").asText()).isEqualTo("15000");
        assertThat(env.path("DATA_FACTOR").asText()).isEqualTo("1");
        assertThat(env.path("PACKED").asText()).isEqualTo("1");
        assertThat(env.path("ANTIALIASED").asText()).isEqualTo("1");
        assertThat(env.path("MAX_LONG_EDGE").asText()).isEqualTo("2048");
        assertThat(env.path("EXPECTED_DRIVER_MAJOR").asText()).isEqualTo("550");
        assertThat(task.path("imageSnapshotId").isMissingNode() || task.path("imageSnapshotId").isNull())
                .isTrue();
        assertThat(request.getValue().storage().mode()).isEqualTo("keys");
        assertThat(request.getValue().storage().inputKeys()).containsExactly(
                "datasets/" + jobId + "/images/0001.jpg",
                "datasets/" + jobId + "/images/0002.jpg"
        );
        assertThat(request.getValue().storage().outputKey()).isEqualTo("outputs/" + jobId + "/model.ply");
        verify(eciManagerClient, times(1)).createTask(any());
    }

    @Test
    void bundledBootstrapResourceMatchesTheEciManagerScriptByteForByte() throws IOException {
        ClassPathResource bootstrap = new ClassPathResource("3dgs-bootstrap.py");

        assertThat(bootstrap.exists()).isTrue();
        try (var input = bootstrap.getInputStream()) {
            assertThat(input.readAllBytes()).containsExactly(Files.readAllBytes(
                    Path.of("..", "..", "eci-manager", "scripts", "3dgs-bootstrap.py")
            ));
        }
    }

    @Test
    void unreadableBootstrapKeepsQueuedJobRetryableWithoutCreatingAnEciTask() throws IOException {
        UUID jobId = queuedJob(1, "bootstrap-unreadable-key");
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId);
        Resource unreadableBootstrap = org.mockito.Mockito.mock(Resource.class);
        when(unreadableBootstrap.exists()).thenReturn(true);
        when(unreadableBootstrap.isReadable()).thenReturn(false);
        when(resourceLoader.getResource("classpath:3dgs-bootstrap.py")).thenReturn(unreadableBootstrap);

        dispatchService.dispatchNext();

        TrainingJob deferred = jobs.findById(jobId).orElseThrow();
        assertThat(deferred.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(deferred.getDispatchNextAttemptAt()).isAfter(Instant.now());
        verify(resourceLoader).getResource("classpath:3dgs-bootstrap.py");
        verify(eciManagerClient, never()).createTask(any());
    }

    @Test
    void bootstrapFailureReleasesClaimBeforeCancellationCanMarkAnEciRequestPending() {
        UUID jobId = queuedJob(1, "bootstrap-cancel-race-key");
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId);
        Resource unreadableBootstrap = org.mockito.Mockito.mock(Resource.class);
        when(unreadableBootstrap.exists()).thenReturn(true);
        when(unreadableBootstrap.isReadable()).thenAnswer(invocation -> {
            jobService.cancelJob(jobId);
            return false;
        });
        when(resourceLoader.getResource("classpath:3dgs-bootstrap.py")).thenReturn(unreadableBootstrap);

        dispatchService.dispatchNext();

        TrainingJob cancelled = jobs.findById(jobId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(cancelled.isDispatchClaimActive()).isFalse();
        assertThat(cancelled.getDispatchClaimToken()).isNull();
        assertThat(cancelled.getDispatchClaimedAt()).isNull();
        assertThat(cancelled.isEciCancellationPending()).isFalse();
        dispatchService.recoverPendingJobs();
        verify(queueService, never()).enqueue(jobId);
        verify(eciManagerClient, never()).createTask(any());
    }

    @Test
    void ambiguousPostRetryReusesTheSameAttemptKeyAndDoesNotCreateASecondBinding() {
        UUID jobId = queuedJob(1, "stable-attempt-key");
        UUID taskId = UUID.randomUUID();
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId, jobId, null);
        when(eciManagerClient.createTask(any()))
                .thenThrow(new ResourceAccessException("timed out after request was accepted"))
                .thenReturn(new EciManagerTaskResponse(taskId, "STARTING"));

        dispatchService.dispatchNext();
        TrainingJob afterAmbiguousPost = jobs.findById(jobId).orElseThrow();
        assertThat(afterAmbiguousPost.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(afterAmbiguousPost.getDispatchClaimedAt()).isNotNull();
        assertThat(afterAmbiguousPost.getEciTaskId()).isNull();

        TrainingJob readyToRetry = jobs.findById(jobId).orElseThrow();
        readyToRetry.setDispatchNextAttemptAt(Instant.now().minusSeconds(1));
        jobs.saveAndFlush(readyToRetry);

        dispatchService.dispatchNext();

        TrainingJob persisted = jobs.findById(jobId).orElseThrow();
        assertThat(persisted.getEciTaskId()).isEqualTo(taskId);
        assertThat(persisted.getClientRequestId()).isEqualTo("stable-attempt-key");
        ArgumentCaptor<EciManagerTaskRequest> requests = ArgumentCaptor.forClass(EciManagerTaskRequest.class);
        verify(eciManagerClient, times(2)).createTask(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(EciManagerTaskRequest::clientRequestId)
                .containsExactly("stable-attempt-key", "stable-attempt-key");
    }

    @Test
    void retryCreatesANewAttemptKeyForTheSameBusinessJob() {
        UUID jobId = queuedJob(3, "old-attempt-key");
        TrainingJob failed = jobs.findById(jobId).orElseThrow();
        failed.setStatus(JobStatus.FAILED);
        jobs.saveAndFlush(failed);
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId).thenReturn((UUID) null);
        when(eciManagerClient.createTask(any()))
                .thenReturn(new EciManagerTaskResponse(UUID.randomUUID(), "STARTING"));

        jobService.retryJob(jobId);
        dispatchService.dispatchNext();

        TrainingJob retried = jobs.findById(jobId).orElseThrow();
        assertThat(retried.getId()).isEqualTo(jobId);
        assertThat(retried.getDispatchAttempt()).isEqualTo(4);
        assertThat(retried.getClientRequestId()).isNotEqualTo("old-attempt-key");
        ArgumentCaptor<EciManagerTaskRequest> request = ArgumentCaptor.forClass(EciManagerTaskRequest.class);
        verify(eciManagerClient).createTask(request.capture());
        assertThat(request.getValue().clientRequestId()).isEqualTo(retried.getClientRequestId());
    }

    @Test
    void queuedDatabaseRowRepairsAnEmptyRedisListAfterAProcessInterruption() {
        UUID jobId = queuedJob(2, "interrupted-attempt-key");
        TrainingJob interrupted = jobs.findById(jobId).orElseThrow();
        interrupted.setDispatchClaimedAt(Instant.now().minusSeconds(3600));
        jobs.saveAndFlush(interrupted);
        when(queueService.queueDepth()).thenReturn(0L);

        dispatchService.recoverPendingJobs();

        verify(queueService).enqueue(jobId);
        assertThat(jobs.findById(jobId)).isPresent();
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void failedDispatchWaitsForBackoffBeforeRecoveryRequeuesIt() {
        UUID jobId = queuedJob(1, "retry-after-failure-key");
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId, jobId);
        when(eciManagerClient.createTask(any()))
                .thenThrow(new ResourceAccessException("ECI manager unavailable"));

        dispatchService.dispatchNext();
        dispatchService.dispatchNext();

        verify(queueService, never()).enqueue(jobId);
        verify(eciManagerClient, times(1)).createTask(any());
        dispatchService.recoverPendingJobs();
        verify(queueService, never()).enqueue(jobId);

        TrainingJob deferred = jobs.findById(jobId).orElseThrow();
        assertThat(deferred.getDispatchNextAttemptAt()).isAfter(Instant.now());
        deferred.setDispatchNextAttemptAt(Instant.now().minusSeconds(1));
        jobs.saveAndFlush(deferred);

        dispatchService.recoverPendingJobs();

        verify(queueService).enqueue(jobId);
    }

    @Test
    void onlyTheAttemptThatMadeThePostCanBindItsReturnedEciTask() {
        UUID jobId = queuedJob(5, "attempt-five-key");
        UUID staleTaskId = UUID.randomUUID();
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId).thenReturn((UUID) null);
        when(eciManagerClient.createTask(any())).thenAnswer(invocation -> {
            TrainingJob replaced = jobs.findById(jobId).orElseThrow();
            replaced.setDispatchAttempt(6);
            replaced.setClientRequestId("attempt-six-key");
            jobs.saveAndFlush(replaced);
            return new EciManagerTaskResponse(staleTaskId, "STARTING");
        });

        dispatchService.dispatchNext();

        TrainingJob current = jobs.findById(jobId).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(current.getEciTaskId()).isNull();
        assertThat(current.getDispatchAttempt()).isEqualTo(6);
        assertThat(current.getClientRequestId()).isEqualTo("attempt-six-key");
    }

    @Test
    void eciTerminalStatusUpdatesTheBusinessJobAndPublishesTheSameStatusToSse() {
        UUID jobId = queuedJob(1, "bound-key");
        UUID taskId = UUID.randomUUID();
        TrainingJob running = jobs.findById(jobId).orElseThrow();
        running.setStatus(JobStatus.RUNNING);
        running.setEciTaskId(taskId);
        jobs.saveAndFlush(running);
        when(eciManagerClient.getTask(taskId)).thenReturn(new EciManagerTaskResponse(taskId, "SUCCEEDED"));

        dispatchService.pollBoundJobs();

        TrainingJob completed = jobs.findById(jobId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(completed.getProgress()).isEqualTo(100);
        assertThat(completed.getOutputOssKey()).isEqualTo("outputs/" + jobId + "/model.ply");
        verify(sseService).publish(jobId, JobStatus.COMPLETED, 100, "completed", "训练完成");
    }

    @ParameterizedTest
    @CsvSource({"FAILED,FAILED", "CANCELLED,CANCELLED"})
    void failedAndCancelledEciTerminalStatusesAreProjectedToBusinessStatus(
            String eciStatus,
            JobStatus expectedStatus
    ) {
        UUID jobId = queuedJob(1, "terminal-key");
        UUID taskId = UUID.randomUUID();
        TrainingJob running = jobs.findById(jobId).orElseThrow();
        running.setStatus(JobStatus.RUNNING);
        running.setEciTaskId(taskId);
        jobs.saveAndFlush(running);
        when(eciManagerClient.getTask(taskId)).thenReturn(new EciManagerTaskResponse(taskId, eciStatus));

        dispatchService.pollBoundJobs();

        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(expectedStatus);
        verify(sseService).publish(any(), org.mockito.ArgumentMatchers.eq(expectedStatus), anyInt(), any(), any());
    }

    @Test
    void stalePollCannotReplaceCancellationThatHappensDuringTheEciRequest() {
        UUID jobId = queuedJob(1, "cancelled-key");
        UUID taskId = UUID.randomUUID();
        TrainingJob running = jobs.findById(jobId).orElseThrow();
        running.setStatus(JobStatus.RUNNING);
        running.setEciTaskId(taskId);
        jobs.saveAndFlush(running);
        when(eciManagerClient.getTask(taskId)).thenAnswer(invocation -> {
            TrainingJob cancelled = jobs.findById(jobId).orElseThrow();
            cancelled.setStatus(JobStatus.CANCELLED);
            jobs.saveAndFlush(cancelled);
            return new EciManagerTaskResponse(taskId, "SUCCEEDED");
        });

        dispatchService.pollBoundJobs();

        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.CANCELLED);
        verify(sseService, never()).publish(jobId, JobStatus.COMPLETED, 100, "completed", "训练完成");
    }

    @Test
    void cancelRacingAnAcceptedPostRecoversByTheOriginalKeyAndRetriesCancelUntilTerminal() {
        UUID jobId = queuedJob(2, "accepted-before-cancel-key");
        UUID taskId = UUID.randomUUID();
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId).thenReturn(jobId).thenReturn(null);
        when(eciManagerClient.createTask(any())).thenAnswer(invocation -> {
            jobService.cancelJob(jobId);
            throw new ResourceAccessException("response lost after ECI accepted the task");
        }).thenReturn(new EciManagerTaskResponse(taskId, "STARTING"));
        when(eciManagerClient.cancelTask(taskId))
                .thenReturn(new EciManagerTaskResponse(taskId, "RUNNING"))
                .thenReturn(new EciManagerTaskResponse(taskId, "CANCELLED"));

        dispatchService.dispatchNext();

        TrainingJob cancelledWithoutBinding = jobs.findById(jobId).orElseThrow();
        assertThat(cancelledWithoutBinding.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(cancelledWithoutBinding.getClientRequestId()).isEqualTo("accepted-before-cancel-key");
        assertThat(cancelledWithoutBinding.getEciTaskId()).isNull();
        assertThatThrownBy(() -> jobService.retryJob(jobId)).isInstanceOf(BusinessException.class);

        dispatchService.recoverPendingJobs();
        verify(queueService, never()).enqueue(jobId);
        TrainingJob dueForCancellationRecovery = jobs.findById(jobId).orElseThrow();
        assertThat(dueForCancellationRecovery.getDispatchNextAttemptAt()).isAfter(Instant.now());
        dueForCancellationRecovery.setDispatchNextAttemptAt(Instant.now().minusSeconds(1));
        jobs.saveAndFlush(dueForCancellationRecovery);
        dispatchService.recoverPendingJobs();
        dispatchService.dispatchNext();
        dispatchService.pollBoundJobs();
        dispatchService.pollBoundJobs();

        TrainingJob converged = jobs.findById(jobId).orElseThrow();
        assertThat(converged.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(converged.getEciTaskId()).isEqualTo(taskId);
        verify(eciManagerClient, times(2)).createTask(any());
        verify(eciManagerClient, times(2)).cancelTask(taskId);
    }

    @Test
    void missingImageOrApiKeyLeavesTheJobVisibleAndQueuedForRetry() {
        UUID jobId = queuedJob(1, "missing-config-key");
        UUID taskId = UUID.randomUUID();
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId, jobId, jobId, jobId);
        when(eciManagerClient.createTask(any())).thenReturn(new EciManagerTaskResponse(taskId, "STARTING"));
        TrainingDispatchService missingImage = serviceWithProperties(new EciManagerProperties(
                "http://127.0.0.1:1", "test-key", Duration.ofMillis(125), Duration.ofMillis(750), ""));
        TrainingDispatchService missingApiKey = serviceWithProperties(new EciManagerProperties(
                "http://127.0.0.1:1", " ", Duration.ofMillis(125), Duration.ofMillis(750), "worker:test"));

        missingImage.dispatchNext();
        missingApiKey.dispatchNext();
        dispatchService.dispatchNext();

        assertThat(jobs.findById(jobId)).isPresent();
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(jobs.findById(jobId).orElseThrow().getDispatchNextAttemptAt()).isAfter(Instant.now());
        verify(eciManagerClient, never()).createTask(any());
        verify(queueService, never()).enqueue(jobId);
        dispatchService.recoverPendingJobs();
        verify(queueService, never()).enqueue(jobId);

        TrainingJob ready = jobs.findById(jobId).orElseThrow();
        ready.setDispatchNextAttemptAt(Instant.now().minusSeconds(1));
        jobs.saveAndFlush(ready);
        dispatchService.recoverPendingJobs();
        verify(queueService).enqueue(jobId);

        dispatchService.dispatchNext();

        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.RUNNING);
        verify(eciManagerClient).createTask(any());
    }

    private UUID queuedJob(int attempt, String clientRequestId) {
        UUID jobId = UUID.randomUUID();
        TrainingJob job = new TrainingJob();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());
        job.setUserId(UUID.randomUUID());
        job.setName("dispatch-test");
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

    private void saveAsset(UUID jobId, String name, String key) {
        DatasetAsset asset = new DatasetAsset();
        asset.setJobId(jobId);
        asset.setFileName(name);
        asset.setOssKey(key);
        asset.setSizeBytes(10);
        asset.setContentType("image/jpeg");
        assets.saveAndFlush(asset);
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

    private void assertBootstrapInvocation(java.util.List<String> args) throws IOException {
        assertThat(args).hasSize(2);
        assertThat(args.get(0)).isEqualTo("shell");
        Matcher matcher = Pattern.compile(
                "^python3 -c 'import base64; exec\\(base64\\.b64decode\\(\\\"([A-Za-z0-9+/=]+)\\\"\\)\\.decode\\(\\)\\)'$"
        ).matcher(args.get(1));
        assertThat(matcher.matches()).isTrue();
        byte[] injectedBootstrap = Base64.getDecoder().decode(matcher.group(1));
        try (var input = new ClassPathResource("3dgs-bootstrap.py").getInputStream()) {
            assertThat(injectedBootstrap).containsExactly(input.readAllBytes());
        }
    }
}
