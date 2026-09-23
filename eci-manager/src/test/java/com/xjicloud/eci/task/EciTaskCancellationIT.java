package com.xjicloud.eci.task;

import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:eci-task-cancellation-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class EciTaskCancellationIT {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

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
    void cancellationStaysPendingUntilDeleteIsObservedTerminated() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "cancel-cloud-id";
        saveTask(taskId, instanceId, TaskStatus.RUNNING);
        saveInstance(taskId, instanceId, cloudId, InstanceStatus.RUNNING);
        saveCreate(instanceId, LifecycleActionStatus.SUCCEEDED, LifecycleActionPhase.WAIT_RUNNING, 1);

        assertThat(taskService.cancel(taskId).status().name()).isEqualTo("CANCELLING");
        assertThat(taskRepository.findById(taskId).orElseThrow().getEndedAt()).isNull();
        assertThat(taskService.findActiveForWatch()).extracting(EciTaskEntity::getId).doesNotContain(taskId);

        LifecycleActionEntity delete = deleteAction(instanceId);
        processDue(delete);

        EciTaskEntity cancelling = taskRepository.findById(taskId).orElseThrow();
        assertThat(cancelling.getStatus().name()).isEqualTo("CANCELLING");
        assertThat(cancelling.getEndedAt()).isNull();
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);

        when(provider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Deleted", null));
        processDue(actionRepository.findById(delete.getId()).orElseThrow());

        EciTaskEntity cancelled = taskRepository.findById(taskId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(cancelled.getEndedAt()).isNotNull();
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.TERMINATED);
        assertThat(actionRepository.findById(delete.getId()).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.SUCCEEDED);
    }

    @Test
    void cancellationWithoutCloudIdConvergesWhenCreateWasNeverSent() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        saveTask(taskId, instanceId, TaskStatus.STARTING);
        saveInstance(taskId, instanceId, null, InstanceStatus.CREATING);
        saveCreate(instanceId, LifecycleActionStatus.READY, LifecycleActionPhase.CREATE_REQUEST, 0);

        assertThat(taskService.cancel(taskId).status().name()).isEqualTo("CANCELLING");
        LifecycleActionEntity delete = deleteAction(instanceId);

        processDue(delete);

        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.CANCELLED);
        assertThat(taskRepository.findById(taskId).orElseThrow().getEndedAt()).isNotNull();
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.TERMINATED);
        assertThat(actionRepository.findById(delete.getId()).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.SUCCEEDED);
    }

    @Test
    void cancellationConvergesWhenInstanceWasAlreadyTerminated() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        saveTask(taskId, instanceId, TaskStatus.RUNNING);
        saveInstance(taskId, instanceId, "already-deleted-cloud-id", InstanceStatus.TERMINATED);

        assertThat(taskService.cancel(taskId).status()).isEqualTo(TaskStatus.CANCELLED);

        EciTaskEntity cancelled = taskRepository.findById(taskId).orElseThrow();
        assertThat(cancelled.getEndedAt()).isNotNull();
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.TERMINATED);
        assertThat(actionRepository.findByInstanceIdAndActionType(instanceId, LifecycleActionType.DELETE))
                .isEmpty();
    }

    private void processDue(LifecycleActionEntity action) {
        Instant due = action.getNextAttemptAt();
        ClaimedLifecycleAction claimed = actionService.claim(action.getId(), due).orElseThrow();
        processor.process(action.getId(), claimed.leaseToken());
    }

    private LifecycleActionEntity deleteAction(UUID instanceId) {
        return actionRepository.findByInstanceIdAndActionType(instanceId, LifecycleActionType.DELETE)
                .orElseThrow();
    }

    private void saveTask(UUID taskId, UUID instanceId, TaskStatus status) {
        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setName("cancel-task");
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

    private void saveInstance(UUID taskId, UUID instanceId, String cloudId, InstanceStatus status) {
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(instanceId);
        instance.setTaskId(taskId);
        instance.setName("cancel-instance");
        instance.setImage("nginx:alpine");
        instance.setCpu(1.0);
        instance.setMemory(2.0);
        instance.setStatus(status);
        instance.setCloudInstanceId(cloudId);
        instance.setCloudStatus(cloudId == null ? null : "Running");
        instance.setEnvJson("{}");
        instance.setCreatedAt(NOW);
        instance.setUpdatedAt(NOW);
        instanceRepository.saveAndFlush(instance);
    }

    private void saveCreate(
            UUID instanceId, LifecycleActionStatus status, LifecycleActionPhase phase, int attempts) {
        LifecycleActionEntity create = new LifecycleActionEntity();
        create.setId(UUID.randomUUID());
        create.setInstanceId(instanceId);
        create.setActionType(LifecycleActionType.CREATE);
        create.setPhase(phase);
        create.setStatus(status);
        create.setClientToken(instanceId.toString());
        create.setAttemptCount(attempts);
        create.setNextAttemptAt(NOW);
        create.setCreatedAt(NOW);
        create.setUpdatedAt(NOW);
        if (status == LifecycleActionStatus.SUCCEEDED) {
            create.setCompletedAt(NOW);
        }
        actionRepository.saveAndFlush(create);
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
