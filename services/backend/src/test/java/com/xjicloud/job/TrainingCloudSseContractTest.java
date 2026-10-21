package com.xjicloud.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.admin.EciManagerClient;
import com.xjicloud.admin.EciManagerTaskResponse;
import com.xjicloud.config.EciManagerProperties;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.project.ProjectService;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.sse.JobProgressSseService;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@DataJpaTest
@Import({TrainingDispatchService.class, TrainingJobService.class, JobProgressSseService.class,
        TrainingCloudSseContractTest.MapperConfig.class})
@EnableConfigurationProperties(EciManagerProperties.class)
@TestPropertySource(properties = {
        "xjicloud.eci-manager.base-url=http://127.0.0.1:1",
        "xjicloud.eci-manager.api-key=test-eci-key",
        "xjicloud.eci-manager.training-image=registry.example.test/xjicloud/worker:test"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TrainingCloudSseContractTest {

    @Autowired
    private TrainingJobRepository jobs;

    @Autowired
    private TrainingDispatchService dispatchService;

    @Autowired
    private TrainingJobService jobService;

    @Autowired
    private JobProgressSseService sseService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private RedisQueueService queueService;

    @MockBean
    private EciManagerClient eciManagerClient;

    @MockBean
    private OssStorageService ossStorageService;

    @BeforeEach
    void clearRows() {
        jobs.deleteAll();
    }

    @Test
    void cancellationSseContainsPendingStateAndIsSentOnlyAfterTheTransactionCommits() {
        UUID jobId = runningJobWithEciTask();
        CapturingEmitter emitter = attachEmitter(jobId);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        transaction.execute(status -> {
            jobService.cancelJob(jobId);
            assertThat(emitter.payloads).as("SSE must wait for the enclosing transaction commit").isEmpty();
            return null;
        });

        assertThat(emitter.payloads).hasSize(1);
        JsonNode payload = parse(emitter.payloads.get(0));
        assertThat(payload.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(payload.has("computeStatus")).isTrue();
        assertThat(payload.has("cloudStatus")).isTrue();
        assertThat(payload.has("computeStatusReason")).isTrue();
        assertThat(payload.path("cancellationPending").asBoolean()).isTrue();
        assertThat(payload.path("cleanupPending").asBoolean()).isTrue();
        assertThat(payload.path("updatedAt").isMissingNode()).isFalse();
    }

    @Test
    void confirmedCancellationCleanupPublishesAConvergenceEvent() {
        UUID jobId = runningJobWithEciTask();
        UUID taskId = jobs.findById(jobId).orElseThrow().getEciTaskId();
        TrainingJob cancelled = jobs.findById(jobId).orElseThrow();
        cancelled.setStatus(JobStatus.CANCELLED);
        cancelled.setEciCancellationPending(true);
        cancelled.setMessage("正在取消 ECI 任务");
        jobs.saveAndFlush(cancelled);
        CapturingEmitter emitter = attachEmitter(jobId);
        EciManagerTaskResponse confirmedSnapshot = snapshot(
                taskId, "CANCELLED", "Deleted", "DELETE_COMPLETE", "Instance removed", true, null
        );
        when(eciManagerClient.cancelTask(taskId)).thenReturn(confirmedSnapshot);

        dispatchService.pollBoundJobs();

        TrainingJob converged = jobs.findById(jobId).orElseThrow();
        assertThat(converged.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(converged.isEciCancellationPending()).isFalse();
        assertThat(emitter.payloads).hasSize(1);
        JsonNode payload = parse(emitter.payloads.get(0));
        assertThat(payload.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(payload.path("cancellationPending").asBoolean()).isFalse();
        assertThat(payload.path("cleanupPending").asBoolean()).isFalse();
        assertThat(payload.path("updatedAt").isMissingNode()).isFalse();
    }

    private UUID runningJobWithEciTask() {
        UUID jobId = UUID.randomUUID();
        TrainingJob job = new TrainingJob();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());
        job.setUserId(UUID.randomUUID());
        job.setName("sse-contract-test");
        job.setStatus(JobStatus.RUNNING);
        job.setStage("training");
        job.setMessage("算力容器已开始训练");
        job.setEciTaskId(UUID.randomUUID());
        job.setDispatchAttempt(1);
        job.setClientRequestId("sse-contract-attempt");
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jobs.saveAndFlush(job);
        return jobId;
    }

    private CapturingEmitter attachEmitter(UUID jobId) {
        @SuppressWarnings("unchecked")
        Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
                (Map<UUID, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(sseService, "emitters");
        CapturingEmitter emitter = new CapturingEmitter();
        emitters.put(jobId, new CopyOnWriteArrayList<>(List.of(emitter)));
        return emitter;
    }

    private JsonNode parse(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception ex) {
            throw new AssertionError("SSE payload must be valid JSON", ex);
        }
    }

    private EciManagerTaskResponse snapshot(
            UUID taskId,
            String status,
            String instanceStatus,
            String cloudStatus,
            String statusReason,
            boolean cleanupConfirmed,
            String failureReason
    ) {
        EciManagerTaskResponse response = Mockito.mock(EciManagerTaskResponse.class);
        when(response.id()).thenReturn(taskId);
        when(response.status()).thenReturn(status);
        stubSnapshotValue(response, "instanceStatus", instanceStatus);
        stubSnapshotValue(response, "cloudStatus", cloudStatus);
        stubSnapshotValue(response, "statusReason", statusReason);
        stubSnapshotValue(response, "cleanupConfirmed", cleanupConfirmed);
        stubSnapshotValue(response, "failureReason", failureReason);
        return response;
    }

    private void stubSnapshotValue(EciManagerTaskResponse response, String property, Object value) {
        Method accessor;
        try {
            accessor = EciManagerTaskResponse.class.getMethod(property);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("Task response must expose " + property, ex);
        }
        try {
            Mockito.when(accessor.invoke(response)).thenReturn(value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot stub task response property " + property, ex);
        }
    }

    @TestConfiguration
    static class MapperConfig {
        @Bean
        @Primary
        ObjectMapper sseContractObjectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    private static final class CapturingEmitter extends SseEmitter {
        private final List<String> payloads = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            for (ResponseBodyEmitter.DataWithMediaType item : builder.build()) {
                Object data = item.getData();
                if (data instanceof String json && json.startsWith("{")) {
                    payloads.add(json);
                }
            }
        }
    }
}
