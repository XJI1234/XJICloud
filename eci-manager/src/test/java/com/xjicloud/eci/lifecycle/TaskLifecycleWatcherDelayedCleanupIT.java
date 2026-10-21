package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.lifecycle.action.ClaimedLifecycleAction;
import com.xjicloud.eci.lifecycle.action.LifecycleActionEntity;
import com.xjicloud.eci.lifecycle.action.LifecycleActionPhase;
import com.xjicloud.eci.lifecycle.action.LifecycleActionProcessor;
import com.xjicloud.eci.lifecycle.action.LifecycleActionRepository;
import com.xjicloud.eci.lifecycle.action.LifecycleActionService;
import com.xjicloud.eci.lifecycle.action.LifecycleActionStatus;
import com.xjicloud.eci.lifecycle.action.LifecycleActionType;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskRepository;
import com.xjicloud.eci.task.EciTaskService;
import com.xjicloud.eci.task.TaskStatus;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.task-poll-interval-sec=3600",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:eci-delayed-success-cleanup-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class TaskLifecycleWatcherDelayedCleanupIT {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    @Autowired
    private TaskLifecycleWatcher watcher;
    @Autowired
    private StartupCompensationRunner startupCompensationRunner;
    @Autowired
    private LifecycleActionProcessor processor;
    @Autowired
    private LifecycleActionService actionService;
    @Autowired
    private EciInstanceService instanceService;
    @Autowired
    private LifecycleActionRepository actionRepository;
    @Autowired
    private EciInstanceRepository instanceRepository;
    @Autowired
    private EciTaskRepository taskRepository;
    @Autowired
    private EciTaskService taskService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private EciProvider provider;

    @Test
    void observedSuccessSurvivesCleanupDelayBeyondMaxRuntime() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID deleteActionId = UUID.randomUUID();
        String cloudId = "delayed-success-cleanup-" + instanceId;
        saveTask(taskId, instanceId);
        saveSucceededStoppingInstance(taskId, instanceId, cloudId);
        saveDeleteAction(deleteActionId, instanceId);

        watcher.pollOnce();

        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);

        when(provider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Deleted", null));
        ClaimedLifecycleAction claim = actionService.claim(deleteActionId, NOW.plusSeconds(1)).orElseThrow();
        processor.process(deleteActionId, claim.leaseToken());
        watcher.pollOnce();

        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(taskService.get(taskId).cleanupConfirmed()).isTrue();
        assertThat(actionRepository.findById(deleteActionId).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.SUCCEEDED);
    }

    @Test
    void refreshedMissingSuccessfulInstanceSurvivesTimeoutAdjudication() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "refreshed-delayed-success-cleanup-" + instanceId;
        saveTask(taskId, instanceId);
        saveSucceededStoppingInstance(taskId, instanceId, cloudId);

        EciTaskEntity task = taskRepository.findById(taskId).orElseThrow();
        task.setContainerLogTail("saved diagnostic tail");
        taskRepository.saveAndFlush(task);
        when(provider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "NotFound", null));

        var refreshed = instanceService.refresh(instanceId);
        assertThat(refreshed.status()).isEqualTo(InstanceStatus.TERMINATED);

        watcher.pollOnce();

        EciTaskEntity completed = taskRepository.findById(taskId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(completed.getFailureReason()).isNull();
        assertThat(completed.getContainerLogTail()).isEqualTo("saved diagnostic tail");
        assertThat(taskService.get(taskId).statusReason()).isEqualTo("trainer completed successfully");
        assertThat(taskService.get(taskId).cleanupConfirmed()).isTrue();
        verify(provider, never()).fetchContainerLog(anyString(), anyInt());
    }

    @Test
    void legacySucceededEvidenceSurvivesRefreshAndStartupRecovery() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "legacy-refreshed-success-" + instanceId;
        saveTask(taskId, instanceId);
        saveSucceededStoppingInstance(taskId, instanceId, cloudId);
        jdbcTemplate.update("UPDATE eci_instance SET cloud_succeeded = FALSE WHERE id = ?", instanceId);
        when(provider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "NotFound", null));

        var refreshed = instanceService.refresh(instanceId);

        assertThat(refreshed.status()).isEqualTo(InstanceStatus.TERMINATED);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().hasCloudSucceeded()).isTrue();

        startupCompensationRunner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(taskService.get(taskId).cleanupConfirmed()).isTrue();
    }

    @Test
    void alwaysPolicyDoesNotPreserveCloudSucceededAsTaskSuccessEvidence() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "always-refreshed-success-" + instanceId;
        saveTask(taskId, instanceId, "Always");
        saveSucceededStoppingInstance(taskId, instanceId, cloudId, "Always");
        when(provider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "NotFound", null));

        var refreshed = instanceService.refresh(instanceId);

        assertThat(refreshed.status()).isEqualTo(InstanceStatus.TERMINATED);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().hasCloudSucceeded()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cloud_succeeded FROM eci_instance WHERE id = ?", Boolean.class, instanceId)).isFalse();
    }

    private void saveTask(UUID taskId, UUID instanceId) {
        saveTask(taskId, instanceId, "Never");
    }

    private void saveTask(UUID taskId, UUID instanceId, String restartPolicy) {
        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setName("delayed-success-task");
        task.setStatus(TaskStatus.RUNNING);
        task.setInstanceId(instanceId);
        task.setImage("nginx:alpine");
        task.setCpu(1.0);
        task.setMemory(2.0);
        task.setRestartPolicy(restartPolicy);
        task.setMaxRuntimeSec(10);
        task.setEnvJson("{}");
        task.setStartedAt(NOW.minusSeconds(60));
        task.setCreatedAt(NOW.minusSeconds(90));
        task.setUpdatedAt(NOW.minusSeconds(60));
        taskRepository.saveAndFlush(task);
    }

    private void saveSucceededStoppingInstance(UUID taskId, UUID instanceId, String cloudId) {
        saveSucceededStoppingInstance(taskId, instanceId, cloudId, "Never");
    }

    private void saveSucceededStoppingInstance(
            UUID taskId, UUID instanceId, String cloudId, String restartPolicy) {
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(instanceId);
        instance.setTaskId(taskId);
        instance.setName("delayed-success-instance");
        instance.setImage("nginx:alpine");
        instance.setCpu(1.0);
        instance.setMemory(2.0);
        instance.setStatus(InstanceStatus.STOPPING);
        instance.setCloudInstanceId(cloudId);
        instance.setRestartPolicy(restartPolicy);
        instance.setCloudStatus("Succeeded");
        instance.setStatusReason("trainer completed successfully");
        instance.setEnvJson("{}");
        instance.setCreatedAt(NOW.minusSeconds(90));
        instance.setUpdatedAt(NOW.minusSeconds(60));
        instanceRepository.saveAndFlush(instance);
    }

    private void saveDeleteAction(UUID actionId, UUID instanceId) {
        LifecycleActionEntity delete = new LifecycleActionEntity();
        delete.setId(actionId);
        delete.setInstanceId(instanceId);
        delete.setActionType(LifecycleActionType.DELETE);
        delete.setPhase(LifecycleActionPhase.WAIT_DELETED);
        delete.setStatus(LifecycleActionStatus.READY);
        delete.setAttemptCount(0);
        delete.setNextAttemptAt(NOW);
        delete.setCreatedAt(NOW);
        delete.setUpdatedAt(NOW);
        actionRepository.saveAndFlush(delete);
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
