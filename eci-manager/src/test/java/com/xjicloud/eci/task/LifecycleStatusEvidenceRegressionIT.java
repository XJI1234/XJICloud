package com.xjicloud.eci.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.config.AsyncConfig;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.lifecycle.TaskLifecycleWatcher;
import com.xjicloud.eci.lifecycle.action.LifecycleActionEntity;
import com.xjicloud.eci.lifecycle.action.LifecycleActionPhase;
import com.xjicloud.eci.lifecycle.action.LifecycleActionProcessor;
import com.xjicloud.eci.lifecycle.action.LifecycleActionRepository;
import com.xjicloud.eci.lifecycle.action.LifecycleActionService;
import com.xjicloud.eci.lifecycle.action.LifecycleActionStatus;
import com.xjicloud.eci.lifecycle.action.LifecycleActionType;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
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
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.task-poll-interval-sec=3600",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:eci-lifecycle-status-evidence-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class LifecycleStatusEvidenceRegressionIT {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TaskLifecycleWatcher watcher;
    @Autowired
    private EciTaskService taskService;
    @Autowired
    private EciTaskRepository taskRepository;
    @Autowired
    private EciInstanceRepository instanceRepository;
    @Autowired
    private LifecycleActionRepository actionRepository;
    @Autowired
    private LifecycleActionService actionService;
    @Autowired
    private LifecycleActionProcessor processor;

    @MockBean
    private EciProvider provider;

    @Test
    void succeededEvidenceSurvivesDeleteIntermediateStatusUntilNotFoundConfirmsCleanup() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "succeeded-cleanup-cloud-id";
        saveTask(taskId, instanceId, TaskStatus.RUNNING);
        saveInstance(taskId, instanceId, cloudId, InstanceStatus.RUNNING, "Running", null);
        saveCreate(instanceId, LifecycleActionStatus.SUCCEEDED, LifecycleActionPhase.WAIT_RUNNING, null);

        when(provider.describe(cloudId)).thenReturn(
                new CloudInstanceView(cloudId, "Succeeded", "trainer exitCode=0 reason=Completed msg=job done"),
                new CloudInstanceView(cloudId, "Deleting", "event=DeleteRequested:cleanup in progress"),
                new CloudInstanceView(cloudId, "NotFound", null));

        watcher.pollOnce();
        LifecycleActionEntity delete = actionRepository.findByInstanceIdAndActionType(
                instanceId, LifecycleActionType.DELETE).orElseThrow();
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getCloudStatus())
                .isEqualTo("Succeeded");

        processDue(delete);
        processDue(actionRepository.findById(delete.getId()).orElseThrow());
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getCloudStatus())
                .isEqualTo("Deleting");
        processDue(actionRepository.findById(delete.getId()).orElseThrow());
        watcher.pollOnce();

        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.TERMINATED);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getCloudStatus())
                .isEqualTo("Succeeded");
        assertCleanupConfirmed(taskSnapshot(taskId), true);
    }

    @Test
    void lateRunningCreateDescribeCannotEraseSucceededEvidenceAfterDeleteCompletes() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "late-running-cloud-id";
        UUID createLease = UUID.randomUUID();
        UUID deleteLease = UUID.randomUUID();
        saveTask(taskId, instanceId, TaskStatus.RUNNING);
        saveInstance(taskId, instanceId, cloudId, InstanceStatus.STOPPING,
                "Succeeded", "trainer exitCode=0 reason=Completed msg=job done");
        LifecycleActionEntity create = saveCreate(
                instanceId, LifecycleActionStatus.IN_PROGRESS, LifecycleActionPhase.WAIT_RUNNING, createLease);
        LifecycleActionEntity delete = saveDelete(
                instanceId, LifecycleActionStatus.IN_PROGRESS, LifecycleActionPhase.WAIT_DELETED, deleteLease);

        actionService.completeInstanceTermination(instanceId, delete.getId(), deleteLease);
        actionService.completeCreateRunning(create.getId(), createLease, "Running", "late Running response");

        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        EciInstanceEntity terminated = instanceRepository.findById(instanceId).orElseThrow();
        assertThat(terminated.getStatus()).isEqualTo(InstanceStatus.TERMINATED);
        assertThat(terminated.getCloudStatus()).isEqualTo("Succeeded");
        assertThat(terminated.getStatusReason()).isEqualTo("trainer exitCode=0 reason=Completed msg=job done");
        assertCleanupConfirmed(taskSnapshot(taskId), true);
    }

    private void processDue(LifecycleActionEntity action) {
        var claimed = actionService.claim(action.getId(), action.getNextAttemptAt()).orElseThrow();
        processor.process(action.getId(), claimed.leaseToken());
    }

    private JsonNode taskSnapshot(UUID taskId) {
        return objectMapper.valueToTree(taskService.get(taskId));
    }

    private void assertCleanupConfirmed(JsonNode snapshot, boolean expected) {
        assertThat(snapshot.path("cleanupConfirmed").isBoolean()).isTrue();
        assertThat(snapshot.path("cleanupConfirmed").asBoolean()).isEqualTo(expected);
    }

    private void saveTask(UUID taskId, UUID instanceId, TaskStatus status) {
        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setName("status-evidence-task");
        task.setStatus(status);
        task.setInstanceId(instanceId);
        task.setImage("nginx:alpine");
        task.setCpu(1.0);
        task.setMemory(2.0);
        task.setRestartPolicy("Never");
        task.setMaxRuntimeSec(600);
        task.setEnvJson("{}");
        task.setStartedAt(status == TaskStatus.RUNNING ? NOW : null);
        task.setCreatedAt(NOW);
        task.setUpdatedAt(NOW);
        taskRepository.saveAndFlush(task);
    }

    private void saveInstance(
            UUID taskId,
            UUID instanceId,
            String cloudId,
            InstanceStatus status,
            String cloudStatus,
            String statusReason) {
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(instanceId);
        instance.setTaskId(taskId);
        instance.setName("status-evidence-instance");
        instance.setImage("nginx:alpine");
        instance.setCpu(1.0);
        instance.setMemory(2.0);
        instance.setStatus(status);
        instance.setCloudInstanceId(cloudId);
        instance.setCloudStatus(cloudStatus);
        instance.setStatusReason(statusReason);
        instance.setEnvJson("{}");
        instance.setCreatedAt(NOW);
        instance.setUpdatedAt(NOW);
        instanceRepository.saveAndFlush(instance);
    }

    private LifecycleActionEntity saveCreate(
            UUID instanceId,
            LifecycleActionStatus status,
            LifecycleActionPhase phase,
            UUID leaseToken) {
        LifecycleActionEntity create = new LifecycleActionEntity();
        create.setId(UUID.randomUUID());
        create.setInstanceId(instanceId);
        create.setActionType(LifecycleActionType.CREATE);
        create.setPhase(phase);
        create.setStatus(status);
        create.setClientToken(instanceId.toString());
        create.setAttemptCount(0);
        create.setNextAttemptAt(NOW);
        create.setLeaseToken(leaseToken);
        create.setLeaseUntil(leaseToken == null ? null : NOW.plusSeconds(60));
        create.setCreatedAt(NOW);
        create.setUpdatedAt(NOW);
        if (status == LifecycleActionStatus.SUCCEEDED) {
            create.setCompletedAt(NOW);
        }
        return actionRepository.saveAndFlush(create);
    }

    private LifecycleActionEntity saveDelete(
            UUID instanceId,
            LifecycleActionStatus status,
            LifecycleActionPhase phase,
            UUID leaseToken) {
        LifecycleActionEntity delete = new LifecycleActionEntity();
        delete.setId(UUID.randomUUID());
        delete.setInstanceId(instanceId);
        delete.setActionType(LifecycleActionType.DELETE);
        delete.setPhase(phase);
        delete.setStatus(status);
        delete.setAttemptCount(0);
        delete.setNextAttemptAt(NOW);
        delete.setLeaseToken(leaseToken);
        delete.setLeaseUntil(leaseToken == null ? null : NOW.plusSeconds(60));
        delete.setCreatedAt(NOW);
        delete.setUpdatedAt(NOW);
        return actionRepository.saveAndFlush(delete);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock lifecycleClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
