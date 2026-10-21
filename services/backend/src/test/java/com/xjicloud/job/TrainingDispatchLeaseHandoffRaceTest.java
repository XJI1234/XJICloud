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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

@DataJpaTest
@Import({TrainingDispatchService.class, TrainingJobService.class})
@EnableConfigurationProperties(EciManagerProperties.class)
@TestPropertySource(properties = {
        "xjicloud.eci-manager.base-url=http://127.0.0.1:1",
        "xjicloud.eci-manager.api-key=test-eci-key",
        "xjicloud.eci-manager.training-image=registry.example.test/xjicloud/worker:test"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TrainingDispatchLeaseHandoffRaceTest {

    @Autowired
    private TrainingJobRepository jobs;

    @Autowired
    private DatasetAssetRepository assets;

    @Autowired
    private TrainingDispatchService dispatchService;

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
    void olderResponseForTakenOverClaimDoesNotCancelTheTaskBeforeNewClaimBindsIt() throws Exception {
        String clientRequestId = "stable-lease-handoff-key";
        UUID jobId = queuedJob(1, clientRequestId);
        UUID taskId = UUID.randomUUID();
        AtomicInteger postCount = new AtomicInteger();
        CountDownLatch oldPostStarted = new CountDownLatch(1);
        CountDownLatch newPostStarted = new CountDownLatch(1);
        CountDownLatch releaseOldResponse = new CountDownLatch(1);
        CountDownLatch releaseNewResponse = new CountDownLatch(1);
        when(queueService.dequeueBlocking(anyLong())).thenReturn(jobId, jobId);
        when(eciManagerClient.createTask(any())).thenAnswer(invocation -> {
            boolean oldPost = postCount.incrementAndGet() == 1;
            CountDownLatch started = oldPost ? oldPostStarted : newPostStarted;
            CountDownLatch release = oldPost ? releaseOldResponse : releaseNewResponse;
            started.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release ECI response");
            }
            return new EciManagerTaskResponse(taskId, "STARTING");
        });

        ExecutorService dispatchers = Executors.newFixedThreadPool(2);
        Future<?> oldDispatch = null;
        Future<?> newDispatch = null;
        try {
            oldDispatch = dispatchers.submit(dispatchService::dispatchNext);
            assertThat(oldPostStarted.await(5, TimeUnit.SECONDS)).isTrue();

            TrainingJob oldClaim = jobs.findById(jobId).orElseThrow();
            assertThat(oldClaim.isDispatchClaimActive()).isTrue();
            assertThat(oldClaim.getEciTaskId()).isNull();
            UUID oldClaimToken = oldClaim.getDispatchClaimToken();
            oldClaim.setDispatchClaimedAt(Instant.now().minus(Duration.ofMinutes(5)));
            jobs.saveAndFlush(oldClaim);

            newDispatch = dispatchers.submit(dispatchService::dispatchNext);
            assertThat(newPostStarted.await(5, TimeUnit.SECONDS)).isTrue();

            TrainingJob newClaim = jobs.findById(jobId).orElseThrow();
            assertThat(newClaim.isDispatchClaimActive()).isTrue();
            assertThat(newClaim.getDispatchClaimToken()).isNotEqualTo(oldClaimToken);
            assertThat(newClaim.getDispatchAttempt()).isEqualTo(1);
            assertThat(newClaim.getClientRequestId()).isEqualTo(clientRequestId);
            assertThat(newClaim.getEciTaskId()).isNull();

            releaseOldResponse.countDown();
            oldDispatch.get(5, TimeUnit.SECONDS);

            TrainingJob betweenResponses = jobs.findById(jobId).orElseThrow();
            assertThat(betweenResponses.getEciTaskId()).isNull();
            verify(eciManagerClient, never()).cancelTask(taskId);

            releaseNewResponse.countDown();
            newDispatch.get(5, TimeUnit.SECONDS);

            TrainingJob bound = jobs.findById(jobId).orElseThrow();
            assertThat(bound.getEciTaskId()).isEqualTo(taskId);
            assertThat(bound.getDispatchAttempt()).isEqualTo(1);
            assertThat(bound.getClientRequestId()).isEqualTo(clientRequestId);
            assertThat(bound.getStatus()).isEqualTo(JobStatus.QUEUED);
            assertThat(bound.getComputeStatus()).isEqualTo("STARTING");
            assertThat(bound.isDispatchClaimActive()).isFalse();

            org.mockito.ArgumentCaptor<EciManagerTaskRequest> requests =
                    org.mockito.ArgumentCaptor.forClass(EciManagerTaskRequest.class);
            verify(eciManagerClient, times(2)).createTask(requests.capture());
            assertThat(requests.getAllValues()).hasSize(2)
                    .allSatisfy(request -> assertThat(request.clientRequestId()).isEqualTo(clientRequestId));
        } finally {
            releaseOldResponse.countDown();
            releaseNewResponse.countDown();
            dispatchers.shutdown();
            assertThat(dispatchers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private UUID queuedJob(int attempt, String clientRequestId) {
        UUID jobId = UUID.randomUUID();
        TrainingJob job = new TrainingJob();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());
        job.setUserId(UUID.randomUUID());
        job.setName("dispatch-lease-handoff-test");
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
}
