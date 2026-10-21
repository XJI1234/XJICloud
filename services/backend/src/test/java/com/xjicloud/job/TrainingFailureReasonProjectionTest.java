package com.xjicloud.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.admin.EciManagerClient;
import com.xjicloud.admin.EciManagerTaskResponse;
import com.xjicloud.auth.UserAccount;
import com.xjicloud.config.EciManagerProperties;
import com.xjicloud.job.dto.JobResponse;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.project.ProjectService;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.sse.JobProgressSseService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@DataJpaTest
@Import({TrainingDispatchService.class, TrainingJobService.class, JobProgressSseService.class,
        TrainingFailureReasonProjectionTest.MapperConfig.class})
@EnableConfigurationProperties(EciManagerProperties.class)
@TestPropertySource(properties = {
        "xjicloud.eci-manager.base-url=http://127.0.0.1:1",
        "xjicloud.eci-manager.api-key=test-eci-key",
        "xjicloud.eci-manager.training-image=registry.example.test/xjicloud/worker:test"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TrainingFailureReasonProjectionTest {

    private static final String RAW_FAILURE_REASON = "account=123456 instance=i-sensitive-9f2a";
    private static final String SAFE_STATUS_REASON = "训练容器因资源不足而停止";
    private static final String WAITING_MESSAGE = "正在确认云端任务状态";

    @Autowired
    private TrainingJobRepository jobs;

    @Autowired
    private TrainingDispatchService dispatchService;

    @Autowired
    private TrainingJobService jobService;

    @Autowired
    private JobProgressSseService sseService;

    @Autowired
    private ObjectMapper objectMapper;

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
    void failedSnapshotWithoutSafeReasonDoesNotExposeRawReasonAndClearsWaitingMessage() {
        UUID jobId = queuedJobWithStartingMessage();
        UUID taskId = jobs.findById(jobId).orElseThrow().getEciTaskId();
        CapturingEmitter emitter = attachEmitter(jobId);
        when(eciManagerClient.getTask(taskId)).thenReturn(failedSnapshot(taskId, "", RAW_FAILURE_REASON));

        dispatchService.pollBoundJobs();

        assertThat(emitter.payloads).hasSize(1);
        JsonNode rest = objectMapper.valueToTree(getJobResponse(jobId));
        JsonNode sse = parse(emitter.payloads.get(0));
        assertThat(rest.path("status").asText()).isEqualTo("FAILED");
        assertThat(rest.path("errorMessage").asText()).isEqualTo("训练失败");
        assertThat(rest.path("computeStatusReason").isNull()).isTrue();
        assertThat(rest.path("message").asText()).isEqualTo("训练失败");
        assertThat(rest.path("message").asText()).isNotEqualTo(WAITING_MESSAGE);
        assertThat(rest.toString()).doesNotContain(RAW_FAILURE_REASON);
        assertThat(sse.path("computeStatusReason").isNull()).isTrue();
        assertThat(sse.path("message").asText()).isEqualTo("训练失败");
        assertThat(emitter.payloads.get(0)).doesNotContain(RAW_FAILURE_REASON);
    }

    @Test
    void failedSnapshotDisplaysSafeStatusReasonWithoutExposingRawReason() {
        UUID jobId = queuedJobWithStartingMessage();
        UUID taskId = jobs.findById(jobId).orElseThrow().getEciTaskId();
        CapturingEmitter emitter = attachEmitter(jobId);
        when(eciManagerClient.getTask(taskId))
                .thenReturn(failedSnapshot(taskId, SAFE_STATUS_REASON, RAW_FAILURE_REASON));

        dispatchService.pollBoundJobs();

        assertThat(emitter.payloads).hasSize(1);
        JsonNode rest = objectMapper.valueToTree(getJobResponse(jobId));
        JsonNode sse = parse(emitter.payloads.get(0));
        assertThat(rest.path("errorMessage").asText()).isEqualTo(SAFE_STATUS_REASON);
        assertThat(rest.path("computeStatusReason").asText()).isEqualTo(SAFE_STATUS_REASON);
        assertThat(rest.path("message").asText()).isEqualTo("训练失败");
        assertThat(rest.toString()).doesNotContain(RAW_FAILURE_REASON);
        assertThat(sse.path("computeStatusReason").asText()).isEqualTo(SAFE_STATUS_REASON);
        assertThat(sse.path("message").asText()).isEqualTo("训练失败");
        assertThat(emitter.payloads.get(0)).doesNotContain(RAW_FAILURE_REASON);
    }

    private UUID queuedJobWithStartingMessage() {
        UUID jobId = UUID.randomUUID();
        TrainingJob job = new TrainingJob();
        job.setId(jobId);
        job.setProjectId(UUID.randomUUID());
        job.setUserId(UUID.randomUUID());
        job.setName("failure-reason-test");
        job.setStatus(JobStatus.QUEUED);
        job.setStage("starting");
        job.setMessage(WAITING_MESSAGE);
        job.setComputeStatus("STARTING");
        job.setComputeStatusReason(WAITING_MESSAGE);
        job.setEciTaskId(UUID.randomUUID());
        job.setDispatchAttempt(1);
        job.setClientRequestId("failure-reason-" + jobId);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jobs.saveAndFlush(job);
        return jobId;
    }

    private EciManagerTaskResponse failedSnapshot(UUID taskId, String statusReason, String failureReason) {
        return new EciManagerTaskResponse(
                taskId, "FAILED", "Stopped", "DELETE_IN_PROGRESS", statusReason, false, failureReason);
    }

    private JobResponse getJobResponse(UUID jobId) {
        UserAccount owner = new UserAccount();
        owner.setId(jobs.findById(jobId).orElseThrow().getUserId());
        return jobService.getJob(owner, jobId);
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
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new AssertionError("SSE payload must be valid JSON", ex);
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
