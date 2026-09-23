package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.lifecycle.StartupCompensationRunner;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskRepository;
import com.xjicloud.eci.task.TaskStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:startup-lifecycle-compensation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class StartupLifecycleActionCompensationIT {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Autowired
    private StartupCompensationRunner runner;
    @Autowired
    private EciInstanceRepository instanceRepository;
    @Autowired
    private EciTaskRepository taskRepository;
    @Autowired
    private LifecycleActionRepository actionRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void clearDatabase() {
        actionRepository.deleteAllInBatch();
        taskRepository.deleteAllInBatch();
        instanceRepository.deleteAllInBatch();
    }

    @Test
    void expiredInProgressLeaseReturnsToWaitingWithoutChangingItsDeadline() {
        UUID instanceId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant nextAttemptAt = now.plusSeconds(30);
        LifecycleActionEntity action = action(instanceId, LifecycleActionType.DELETE,
                LifecycleActionPhase.DELETE_REQUEST, LifecycleActionStatus.IN_PROGRESS);
        action.setLeaseToken(UUID.randomUUID());
        action.setLeaseUntil(now.minusSeconds(1));
        action.setNextAttemptAt(nextAttemptAt);
        actionRepository.saveAndFlush(action);

        runStartupCompensation();

        LifecycleActionEntity recovered = actionRepository.findById(action.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(recovered.getLeaseToken()).isNull();
        assertThat(recovered.getLeaseUntil()).isNull();
        assertThat(recovered.getNextAttemptAt()).isEqualTo(nextAttemptAt);
    }

    @Test
    void creatingWithoutCloudIdGetsQuarantinedUuidTokenCreateAction() {
        EciInstanceEntity instance = instance(InstanceStatus.CREATING, null, null);
        instanceRepository.saveAndFlush(instance);

        runStartupCompensation();

        LifecycleActionEntity create = actionFor(instance.getId(), LifecycleActionType.CREATE);
        assertThat(create.getPhase()).isEqualTo(LifecycleActionPhase.CREATE_REQUEST);
        assertThat(create.getClientToken()).isEqualTo(instance.getId().toString());
        assertThat(create.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(create.getLastError()).contains("Legacy CREATE outcome unknown");
        assertThat(instanceRepository.findById(instance.getId()).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.CREATING);
    }

    @Test
    void creatingWithCloudIdGetsWaitRunningActionWithoutCallingProvider() {
        EciInstanceEntity instance = instance(InstanceStatus.CREATING, "legacy-cloud-id", null);
        instanceRepository.saveAndFlush(instance);

        runStartupCompensation();

        LifecycleActionEntity create = actionFor(instance.getId(), LifecycleActionType.CREATE);
        assertThat(create.getPhase()).isEqualTo(LifecycleActionPhase.WAIT_RUNNING);
        assertThat(create.getClientToken()).isEqualTo(instance.getId().toString());
        assertThat(instanceRepository.findById(instance.getId()).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.CREATING);
    }

    @Test
    void stoppingInstanceGetsDeleteActionWithoutCallingProvider() {
        EciInstanceEntity instance = instance(InstanceStatus.STOPPING, "legacy-cloud-id", null);
        instanceRepository.saveAndFlush(instance);

        runStartupCompensation();

        assertThat(actionFor(instance.getId(), LifecycleActionType.DELETE).getPhase())
                .isEqualTo(LifecycleActionPhase.DELETE_REQUEST);
        assertThat(instanceRepository.findById(instance.getId()).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);
    }

    @Test
    void taskLinkedFailedInstanceWithCloudIdGetsDeleteAction() {
        EciTaskEntity task = task(TaskStatus.FAILED, null);
        taskRepository.saveAndFlush(task);
        EciInstanceEntity instance = instance(InstanceStatus.FAILED, "legacy-cloud-id", task.getId());
        instanceRepository.saveAndFlush(instance);

        runStartupCompensation();

        assertThat(actionFor(instance.getId(), LifecycleActionType.DELETE).getPhase())
                .isEqualTo(LifecycleActionPhase.DELETE_REQUEST);
    }

    @Test
    void manualFailedInstanceIsNotAutomaticallyDeleted() {
        EciInstanceEntity instance = instance(InstanceStatus.FAILED, "manual-cloud-id", null);
        instanceRepository.saveAndFlush(instance);

        runStartupCompensation();

        assertThat(actionRepository.findByInstanceIdAndActionType(
                instance.getId(), LifecycleActionType.DELETE)).isEmpty();
    }

    @Test
    void legacyCancelledTaskWithLiveInstanceReturnsToCancellingAndRequestsDelete() {
        UUID instanceId = UUID.randomUUID();
        EciTaskEntity task = task(TaskStatus.CANCELLED, instanceId);
        task.setEndedAt(NOW.minusSeconds(60));
        taskRepository.saveAndFlush(task);
        EciInstanceEntity instance = instance(InstanceStatus.RUNNING, "legacy-cloud-id", task.getId());
        instance.setId(instanceId);
        instanceRepository.saveAndFlush(instance);

        runStartupCompensation();

        EciTaskEntity migrated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(migrated.getStatus()).isEqualTo(TaskStatus.CANCELLING);
        assertThat(migrated.getEndedAt()).isNull();
        assertThat(actionFor(instance.getId(), LifecycleActionType.DELETE).getPhase())
                .isEqualTo(LifecycleActionPhase.DELETE_REQUEST);
    }

    @Test
    void legacyCancelledTaskWithTerminatedInstanceKeepsItsTerminalEvidence() {
        UUID instanceId = UUID.randomUUID();
        EciTaskEntity task = task(TaskStatus.CANCELLED, instanceId);
        task.setEndedAt(NOW.minusSeconds(60));
        taskRepository.saveAndFlush(task);
        EciInstanceEntity instance = instance(InstanceStatus.TERMINATED, "deleted-cloud-id", task.getId());
        instance.setId(instanceId);
        instanceRepository.saveAndFlush(instance);

        runStartupCompensation();

        EciTaskEntity unchanged = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(unchanged.getEndedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(actionRepository.findByInstanceIdAndActionType(
                instance.getId(), LifecycleActionType.DELETE)).isEmpty();
    }

    @Test
    void malformedCancelledTaskDoesNotPreventLaterTaskMigration() {
        EciTaskEntity malformed = task(TaskStatus.CANCELLED, UUID.randomUUID());
        taskRepository.saveAndFlush(malformed);
        UUID instanceId = UUID.randomUUID();
        EciTaskEntity valid = task(TaskStatus.CANCELLED, instanceId);
        taskRepository.saveAndFlush(valid);
        EciInstanceEntity instance = instance(InstanceStatus.RUNNING, "valid-cloud-id", valid.getId());
        instance.setId(instanceId);
        instanceRepository.saveAndFlush(instance);

        runStartupCompensation();

        assertThat(taskRepository.findById(valid.getId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.CANCELLING);
        assertThat(actionFor(instance.getId(), LifecycleActionType.DELETE).getPhase())
                .isEqualTo(LifecycleActionPhase.DELETE_REQUEST);
        assertThat(taskRepository.findById(malformed.getId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void mismatchedCancelledTaskDoesNotDeleteManualInstanceOrBlockLaterMigration() {
        EciInstanceEntity manualInstance = instance(InstanceStatus.RUNNING, "manual-cloud-id", null);
        instanceRepository.saveAndFlush(manualInstance);
        entityManager.detach(manualInstance);
        EciTaskEntity mismatched = task(TaskStatus.CANCELLED, manualInstance.getId());
        mismatched.setEndedAt(NOW.minusSeconds(60));
        taskRepository.saveAndFlush(mismatched);

        UUID validInstanceId = UUID.randomUUID();
        EciTaskEntity valid = task(TaskStatus.CANCELLED, validInstanceId);
        taskRepository.saveAndFlush(valid);
        EciInstanceEntity validInstance = instance(InstanceStatus.RUNNING, "valid-cloud-id", valid.getId());
        validInstance.setId(validInstanceId);
        instanceRepository.saveAndFlush(validInstance);

        runStartupCompensation();

        EciTaskEntity unchanged = taskRepository.findById(mismatched.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(unchanged.getEndedAt()).isEqualTo(NOW.minusSeconds(60));
        EciInstanceEntity unchangedManual = instanceRepository.findById(manualInstance.getId()).orElseThrow();
        assertThat(unchangedManual).usingRecursiveComparison().isEqualTo(manualInstance);
        assertThat(actionRepository.findByInstanceIdAndActionType(
                manualInstance.getId(), LifecycleActionType.DELETE)).isEmpty();
        assertThat(taskRepository.findById(valid.getId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.CANCELLING);
        assertThat(actionFor(validInstanceId, LifecycleActionType.DELETE).getPhase())
                .isEqualTo(LifecycleActionPhase.DELETE_REQUEST);
    }

    private void runStartupCompensation() {
        runner.run(new DefaultApplicationArguments(new String[0]));
    }

    private LifecycleActionEntity actionFor(UUID instanceId, LifecycleActionType type) {
        return actionRepository.findByInstanceIdAndActionType(instanceId, type).orElseThrow();
    }

    private static EciInstanceEntity instance(InstanceStatus status, String cloudId, UUID taskId) {
        Instant now = NOW;
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(UUID.randomUUID());
        instance.setTaskId(taskId);
        instance.setName("legacy-instance");
        instance.setImage("test:image");
        instance.setCpu(1.0);
        instance.setMemory(2.0);
        instance.setStatus(status);
        instance.setCloudInstanceId(cloudId);
        instance.setEnvJson("{}");
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        return instance;
    }

    private static EciTaskEntity task(TaskStatus status, UUID instanceId) {
        Instant now = NOW;
        EciTaskEntity task = new EciTaskEntity();
        task.setId(UUID.randomUUID());
        task.setName("legacy-task");
        task.setStatus(status);
        task.setInstanceId(instanceId);
        task.setImage("test:image");
        task.setCpu(1.0);
        task.setMemory(2.0);
        task.setRestartPolicy("Never");
        task.setMaxRuntimeSec(60);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private static LifecycleActionEntity action(
            UUID instanceId,
            LifecycleActionType type,
            LifecycleActionPhase phase,
            LifecycleActionStatus status) {
        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(UUID.randomUUID());
        action.setInstanceId(instanceId);
        action.setActionType(type);
        action.setPhase(phase);
        action.setStatus(status);
        action.setAttemptCount(0);
        action.setNextAttemptAt(NOW.minusSeconds(1));
        action.setCreatedAt(NOW.minusSeconds(60));
        action.setUpdatedAt(NOW.minusSeconds(60));
        return action;
    }
}
