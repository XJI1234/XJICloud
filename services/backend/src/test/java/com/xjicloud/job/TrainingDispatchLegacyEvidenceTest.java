package com.xjicloud.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xjicloud.admin.EciManagerClient;
import com.xjicloud.admin.EciManagerTaskResponse;
import com.xjicloud.config.EciManagerProperties;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.project.ProjectService;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.sse.JobProgressSseService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(TrainingDispatchService.class)
@EnableConfigurationProperties(EciManagerProperties.class)
@TestPropertySource(properties = {
        "xjicloud.eci-manager.base-url=http://127.0.0.1:1",
        "xjicloud.eci-manager.api-key=test-eci-key",
        "xjicloud.eci-manager.training-image=registry.example.test/xjicloud/worker:test",
        "xjicloud.eci-manager.connect-timeout=125ms",
        "xjicloud.eci-manager.read-timeout=750ms"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TrainingDispatchLegacyEvidenceTest {

    @Autowired
    private TrainingJobRepository jobs;

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
    void clearJobs() {
        jobs.deleteAll();
    }

    @ParameterizedTest
    @CsvSource({
            "RUNNING,SUCCEEDED,COMPLETED",
            "RUNNING,FAILED,FAILED",
            "STARTING,SUCCEEDED,COMPLETED",
            "STARTING,FAILED,FAILED"
    })
    void activeTaskIsStillPolledAfterCleanupConfirmation(
            String firstStatus,
            String terminalStatus,
            JobStatus expectedStatus
    ) {
        UUID jobId = createQueuedJob("active-after-cleanup");
        UUID taskId = UUID.randomUUID();
        TrainingJob job = jobs.findById(jobId).orElseThrow();
        job.setEciTaskId(taskId);
        jobs.saveAndFlush(job);
        when(eciManagerClient.getTask(taskId))
                .thenReturn(new EciManagerTaskResponse(taskId, firstStatus, null, null, null, true, null))
                .thenReturn(new EciManagerTaskResponse(taskId, terminalStatus));

        dispatchService.pollBoundJobs();
        TrainingJob afterCleanupConfirmation = jobs.findById(jobId).orElseThrow();
        assertThat(afterCleanupConfirmation.isEciCleanupConfirmed()).isTrue();

        dispatchService.pollBoundJobs();

        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(expectedStatus);
        verify(eciManagerClient, times(2)).getTask(taskId);
    }

    private UUID createQueuedJob(String requestId) {
        UUID id = UUID.randomUUID();
        TrainingJob job = new TrainingJob();
        job.setId(id);
        job.setProjectId(UUID.randomUUID());
        job.setUserId(UUID.randomUUID());
        job.setName("dispatch-legacy-test");
        job.setStatus(JobStatus.QUEUED);
        job.setStage("queued");
        job.setInputOssPrefix("datasets/" + id + "/");
        job.setOutputOssKey("outputs/" + id + "/model.ply");
        job.setDispatchAttempt(1);
        job.setClientRequestId(requestId + "-" + id);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        return jobs.saveAndFlush(job).getId();
    }
}
