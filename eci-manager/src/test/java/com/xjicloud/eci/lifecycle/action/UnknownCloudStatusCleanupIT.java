package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskRepository;
import com.xjicloud.eci.task.EciTaskService;
import com.xjicloud.eci.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.task-poll-interval-sec=3600",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:eci-unknown-cloud-status-cleanup-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class UnknownCloudStatusCleanupIT {

    private static final Instant CREATED_AT = Instant.parse("2026-10-21T12:00:00Z");

    @Autowired
    private EciInstanceService instanceService;
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
    void refreshDoesNotConfirmCleanupWhenCloudStatusIsNull() {
        Scenario scenario = saveScenario(false);
        when(provider.describe(scenario.cloudId()))
                .thenReturn(new CloudInstanceView(scenario.cloudId(), null, null));

        var refreshed = instanceService.refresh(scenario.instanceId());

        assertThat(refreshed.status()).isEqualTo(InstanceStatus.STOPPING);
        assertThat(taskService.get(scenario.taskId()).status()).isEqualTo(TaskStatus.CANCELLING);
        assertThat(taskService.get(scenario.taskId()).cleanupConfirmed()).isFalse();
    }

    @Test
    void deleteDoesNotConfirmCleanupWhenCloudStatusIsNull() {
        Scenario scenario = saveScenario(true);
        when(provider.describe(scenario.cloudId()))
                .thenReturn(new CloudInstanceView(scenario.cloudId(), null, null));
        ClaimedLifecycleAction claim = actionService.claim(scenario.deleteActionId(), CREATED_AT)
                .orElseThrow();

        processor.process(scenario.deleteActionId(), claim.leaseToken());

        LifecycleActionEntity delete = actionRepository.findById(scenario.deleteActionId()).orElseThrow();
        assertThat(delete.getPhase()).isEqualTo(LifecycleActionPhase.WAIT_DELETED);
        assertThat(delete.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(instanceRepository.findById(scenario.instanceId()).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);
        assertThat(taskService.get(scenario.taskId()).status()).isEqualTo(TaskStatus.CANCELLING);
        assertThat(taskService.get(scenario.taskId()).cleanupConfirmed()).isFalse();
    }

    private Scenario saveScenario(boolean withDeleteAction) {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "unknown-cloud-status-" + instanceId;

        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setName("unknown-cloud-status-task");
        task.setStatus(TaskStatus.CANCELLING);
        task.setInstanceId(instanceId);
        task.setImage("nginx:alpine");
        task.setCpu(1.0);
        task.setMemory(2.0);
        task.setRestartPolicy("Never");
        task.setMaxRuntimeSec(600);
        task.setEnvJson("{}");
        task.setCreatedAt(CREATED_AT);
        task.setUpdatedAt(CREATED_AT);
        taskRepository.saveAndFlush(task);

        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(instanceId);
        instance.setTaskId(taskId);
        instance.setName("unknown-cloud-status-instance");
        instance.setImage("nginx:alpine");
        instance.setCpu(1.0);
        instance.setMemory(2.0);
        instance.setStatus(InstanceStatus.STOPPING);
        instance.setCloudInstanceId(cloudId);
        instance.setCloudStatus("Deleting");
        instance.setEnvJson("{}");
        instance.setCreatedAt(CREATED_AT);
        instance.setUpdatedAt(CREATED_AT);
        instanceRepository.saveAndFlush(instance);

        UUID deleteActionId = null;
        if (withDeleteAction) {
            LifecycleActionEntity delete = new LifecycleActionEntity();
            deleteActionId = UUID.randomUUID();
            delete.setId(deleteActionId);
            delete.setInstanceId(instanceId);
            delete.setActionType(LifecycleActionType.DELETE);
            delete.setPhase(LifecycleActionPhase.WAIT_DELETED);
            delete.setStatus(LifecycleActionStatus.READY);
            delete.setAttemptCount(0);
            delete.setNextAttemptAt(CREATED_AT);
            delete.setCreatedAt(CREATED_AT);
            delete.setUpdatedAt(CREATED_AT);
            actionRepository.saveAndFlush(delete);
        }
        return new Scenario(taskId, instanceId, cloudId, deleteActionId);
    }

    private record Scenario(UUID taskId, UUID instanceId, String cloudId, UUID deleteActionId) {
    }
}
