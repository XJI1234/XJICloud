package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskRepository;
import com.xjicloud.eci.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/** Detects committing DELETE and instance termination before task terminal persistence. */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "eci.lifecycle-action.lease-sec=60",
        "spring.datasource.url=jdbc:h2:mem:eci-delete-lifecycle-atomicity-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class DeleteLifecycleActionAtomicityIT {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Autowired
    private LifecycleActionProcessor processor;
    @Autowired
    private LifecycleActionService actionService;
    @Autowired
    private LifecycleActionRepository actionRepository;
    @Autowired
    private EciInstanceRepository instanceRepository;
    @SpyBean
    private EciTaskRepository taskRepository;
    @MockBean
    private EciProvider provider;

    @Test
    void taskTerminalPersistenceFailureRollsBackDeleteCompletionAndExpiredLeaseCanBeReclaimed() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        String cloudId = "atomicity-cloud-id";
        saveTask(taskId, instanceId);
        saveInstance(taskId, instanceId, cloudId);
        saveDeleteAction(actionId, instanceId);
        when(provider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Deleted", null));

        ClaimedLifecycleAction initialClaim = actionService.claim(actionId, NOW).orElseThrow();
        doThrow(new IllegalStateException("task terminal persistence failed"))
                .when(taskRepository).markCancelledIfCancelling(
                eq(taskId), eq(TaskStatus.CANCELLED), any(Instant.class), eq(TaskStatus.CANCELLING));

        assertThatThrownBy(() -> processor.process(actionId, initialClaim.leaseToken()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("task terminal persistence failed");

        LifecycleActionEntity afterFailure = actionRepository.findById(actionId).orElseThrow();
        assertThat(afterFailure.getStatus()).isEqualTo(LifecycleActionStatus.IN_PROGRESS);
        assertThat(afterFailure.getPhase()).isEqualTo(LifecycleActionPhase.WAIT_DELETED);
        assertThat(afterFailure.getLeaseToken()).isEqualTo(initialClaim.leaseToken());
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);
        EciTaskEntity cancelling = taskRepository.findById(taskId).orElseThrow();
        assertThat(cancelling.getStatus()).isEqualTo(TaskStatus.CANCELLING);
        assertThat(cancelling.getEndedAt()).isNull();

        ClaimedLifecycleAction retryClaim = actionService.claim(actionId, NOW.plusSeconds(61)).orElseThrow();
        assertThat(retryClaim.leaseToken()).isNotEqualTo(initialClaim.leaseToken());
    }

    private void saveTask(UUID taskId, UUID instanceId) {
        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setName("atomicity-task");
        task.setStatus(TaskStatus.CANCELLING);
        task.setInstanceId(instanceId);
        task.setImage("nginx:alpine");
        task.setCpu(1.0);
        task.setMemory(2.0);
        task.setRestartPolicy("Never");
        task.setMaxRuntimeSec(600);
        task.setEnvJson("{}");
        task.setStartedAt(NOW);
        task.setCreatedAt(NOW);
        task.setUpdatedAt(NOW);
        taskRepository.saveAndFlush(task);
    }

    private void saveInstance(UUID taskId, UUID instanceId, String cloudId) {
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(instanceId);
        instance.setTaskId(taskId);
        instance.setName("atomicity-instance");
        instance.setImage("nginx:alpine");
        instance.setCpu(1.0);
        instance.setMemory(2.0);
        instance.setStatus(InstanceStatus.STOPPING);
        instance.setCloudInstanceId(cloudId);
        instance.setCloudStatus("Running");
        instance.setEnvJson("{}");
        instance.setCreatedAt(NOW);
        instance.setUpdatedAt(NOW);
        instanceRepository.saveAndFlush(instance);
    }

    private void saveDeleteAction(UUID actionId, UUID instanceId) {
        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(actionId);
        action.setInstanceId(instanceId);
        action.setActionType(LifecycleActionType.DELETE);
        action.setPhase(LifecycleActionPhase.WAIT_DELETED);
        action.setStatus(LifecycleActionStatus.READY);
        action.setAttemptCount(0);
        action.setNextAttemptAt(NOW);
        action.setCreatedAt(NOW);
        action.setUpdatedAt(NOW);
        actionRepository.saveAndFlush(action);
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
