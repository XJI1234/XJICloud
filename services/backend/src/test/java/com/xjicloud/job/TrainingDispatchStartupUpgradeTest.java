package com.xjicloud.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.xjicloud.XjiCloudApplication;
import com.xjicloud.admin.EciManagerClient;
import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.BusinessException;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.project.ProjectService;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.sse.JobProgressSseService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

class TrainingDispatchStartupUpgradeTest {

    private static final UUID LEGACY_JOB_ID = UUID.fromString("fd6fb4c9-7d80-4ec4-a07d-2d9b49162951");
    private static final UUID LEGACY_PROJECT_ID = UUID.fromString("d9a2ed16-fc63-4e6c-9ab2-1e0e65d7c410");
    private static final UUID LEGACY_USER_ID = UUID.fromString("d383a65a-9eb3-4cc1-a00b-b4393d9a194c");
    private static final Instant LEGACY_CLAIMED_AT = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void startupMigratesLegacyClaimOnceAndPreservesCreateResponsibilityAcrossCancellationAndRecovery()
            throws Exception {
        String url = "jdbc:h2:mem:training_dispatch_upgrade_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        createLegacyDatabase(url, false);

        try (ConfigurableApplicationContext context = startApplication(url)) {
            TrainingJobRepository jobs = context.getBean(TrainingJobRepository.class);
            TrainingJob migrated = jobs.findById(LEGACY_JOB_ID).orElseThrow();
            assertThat(migrated.getDispatchRequestSentAt()).isEqualTo(LEGACY_CLAIMED_AT);

            TrainingJobService jobService = context.getBean(TrainingJobService.class);
            var cancellation = jobService.cancelJob(LEGACY_JOB_ID);
            assertThat(cancellation.cancellationPending()).isTrue();
            assertThat(cancellation.cleanupPending()).isTrue();

            context.getBean(TrainingDispatchService.class).recoverPendingJobs();
            org.mockito.Mockito.verify(context.getBean(RedisQueueService.class)).enqueue(LEGACY_JOB_ID);

            TrainingJob recovered = jobs.findById(LEGACY_JOB_ID).orElseThrow();
            assertThat(recovered.isDispatchClaimActive()).isFalse();
            assertThat(recovered.getDispatchClaimedAt()).isNull();
            assertThat(recovered.getDispatchRequestSentAt()).isEqualTo(LEGACY_CLAIMED_AT);
            assertThatThrownBy(() -> jobService.deleteOwnedJob(ownerOf(recovered), LEGACY_JOB_ID))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> jobService.retryJob(LEGACY_JOB_ID))
                    .isInstanceOf(BusinessException.class);

            TrainingJob newClaim = new TrainingJob();
            newClaim.setId(UUID.fromString("ae27b529-58bb-4c21-86e8-0c991a91b384"));
            newClaim.setProjectId(UUID.randomUUID());
            newClaim.setUserId(UUID.randomUUID());
            newClaim.setName("new-pre-request-claim");
            newClaim.setStatus(JobStatus.QUEUED);
            newClaim.setStage("queued");
            newClaim.setDispatchAttempt(1);
            newClaim.setClientRequestId("new-pre-request-claim");
            newClaim.setDispatchClaimActive(true);
            newClaim.setDispatchClaimToken(UUID.randomUUID());
            newClaim.setDispatchClaimedAt(Instant.now());
            newClaim.setCreatedAt(Instant.now());
            newClaim.setUpdatedAt(Instant.now());
            jobs.saveAndFlush(newClaim);
            assertThat(newClaim.getDispatchRequestSentAt()).isNull();
        }

        try (ConfigurableApplicationContext restarted = startApplication(url)) {
            TrainingJobRepository jobs = restarted.getBean(TrainingJobRepository.class);
            assertThat(jobs.findById(LEGACY_JOB_ID).orElseThrow().getDispatchRequestSentAt())
                    .isEqualTo(LEGACY_CLAIMED_AT);
            assertThat(jobs.findById(UUID.fromString("ae27b529-58bb-4c21-86e8-0c991a91b384"))
                    .orElseThrow().getDispatchRequestSentAt())
                    .as("a pre-request claim created after upgrade is not treated as legacy evidence")
                    .isNull();
        }
    }

    @Test
    void startupBackfillsLegacyClaimWhenNewColumnsAlreadyExistAsNull() throws Exception {
        String url = "jdbc:h2:mem:training_dispatch_null_evidence_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        createLegacyDatabase(url, true);

        try (ConfigurableApplicationContext context = startApplication(url)) {
            TrainingJob migrated = context.getBean(TrainingJobRepository.class)
                    .findById(LEGACY_JOB_ID)
                    .orElseThrow();

            assertThat(migrated.getDispatchRequestSentAt()).isEqualTo(LEGACY_CLAIMED_AT);
            assertThat(migrated.isEciCleanupConfirmed()).isFalse();
        }
    }

    private ConfigurableApplicationContext startApplication(String url) {
        return new SpringApplicationBuilder(XjiCloudApplication.class, TestOverrides.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--server.port=0",
                        "--xjicloud.eci-manager.scheduler-enabled=false",
                        "--xjicloud.storage.root=/tmp/xjicloud-training-startup-upgrade",
                        "--xjicloud.eci-manager.api-key=test-key",
                        "--xjicloud.eci-manager.training-image=test-image",
                        "--xjicloud.oss.endpoint=http://127.0.0.1:9000",
                        "--xjicloud.oss.region=us-east-1",
                        "--xjicloud.oss.bucket=test-bucket",
                        "--xjicloud.oss.access-key=test-access-key",
                        "--xjicloud.oss.secret-key=test-secret-key",
                        "--xjicloud.admin.default-username=startup-test-admin",
                        "--xjicloud.admin.default-password=startup-test-password"
                );
    }

    private void createLegacyDatabase(String url, boolean includeNewColumns) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            String additionalColumns = includeNewColumns ? """
                        , dispatch_request_sent_at TIMESTAMP WITH TIME ZONE
                        , compute_status VARCHAR(128)
                        , cloud_status VARCHAR(128)
                        , compute_status_reason VARCHAR(512)
                        , eci_cleanup_confirmed BOOLEAN NOT NULL DEFAULT FALSE
                    """ : "";
            statement.execute("""
                    CREATE TABLE training_jobs (
                        id UUID PRIMARY KEY,
                        project_id UUID NOT NULL,
                        user_id UUID NOT NULL,
                        name VARCHAR(256) NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        progress INTEGER NOT NULL,
                        stage VARCHAR(128),
                        message VARCHAR(512),
                        input_oss_prefix VARCHAR(512),
                        output_oss_key VARCHAR(512),
                        output_download_url VARCHAR(1024),
                        worker_id UUID,
                        dispatch_attempt INTEGER NOT NULL,
                        client_request_id VARCHAR(128),
                        eci_task_id UUID,
                        dispatch_claimed_at TIMESTAMP WITH TIME ZONE,
                        dispatch_claim_active BOOLEAN NOT NULL,
                        dispatch_claim_token UUID,
                        dispatch_enqueued_at TIMESTAMP WITH TIME ZONE,
                        dispatch_next_attempt_at TIMESTAMP WITH TIME ZONE,
                        eci_cancellation_pending BOOLEAN NOT NULL,
                        error_message VARCHAR(1024),
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                    %s
                    )
                    """.formatted(additionalColumns));
            statement.execute("""
                    INSERT INTO training_jobs (
                        id, project_id, user_id, name, status, progress, stage,
                        input_oss_prefix, output_oss_key, dispatch_attempt, client_request_id,
                        dispatch_claimed_at, dispatch_claim_active, dispatch_claim_token,
                        eci_cancellation_pending, created_at, updated_at
                    ) VALUES (
                        'fd6fb4c9-7d80-4ec4-a07d-2d9b49162951',
                        'd9a2ed16-fc63-4e6c-9ab2-1e0e65d7c410',
                        'd383a65a-9eb3-4cc1-a00b-b4393d9a194c',
                        'legacy-active-claim', 'QUEUED', 0, 'queued',
                        'datasets/legacy/', 'outputs/legacy/model.ply', 1, 'legacy-active-claim',
                        TIMESTAMP WITH TIME ZONE '2026-10-20 12:00:00+00:00', TRUE,
                        '5016a1e4-1824-4905-998c-8bf08ffed085', FALSE,
                        TIMESTAMP WITH TIME ZONE '2026-10-20 11:00:00+00:00',
                        TIMESTAMP WITH TIME ZONE '2026-10-20 12:00:00+00:00'
                    )
                    """);
        }
    }

    private UserAccount ownerOf(TrainingJob job) {
        UserAccount owner = new UserAccount();
        owner.setId(job.getUserId());
        return owner;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {

        @Bean
        @Primary
        ProjectService testProjectService() {
            return mock(ProjectService.class);
        }

        @Bean
        @Primary
        RedisQueueService testQueueService() {
            return mock(RedisQueueService.class);
        }

        @Bean
        @Primary
        EciManagerClient testEciManagerClient() {
            return mock(EciManagerClient.class);
        }

        @Bean
        @Primary
        OssStorageService testOssStorageService() {
            return mock(OssStorageService.class);
        }

        @Bean
        @Primary
        JobProgressSseService testJobProgressSseService() {
            return mock(JobProgressSseService.class);
        }
    }
}
