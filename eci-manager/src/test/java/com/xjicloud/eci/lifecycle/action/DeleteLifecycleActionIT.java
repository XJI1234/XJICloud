package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.lifecycle.TaskLifecycleWatcher;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskRepository;
import com.xjicloud.eci.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.task-poll-interval-sec=3600",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "eci.failure-log-grace-sec=37",
        "ECI_FAILURE_LOG_GRACE_SEC=37",
        "spring.datasource.url=jdbc:h2:mem:eci-delete-lifecycle-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class DeleteLifecycleActionIT {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Autowired
    private TaskLifecycleWatcher watcher;
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
    @Autowired
    private EciProperties properties;
    @Autowired
    private Clock clock;

    @MockBean
    private EciProvider provider;

    @Test
    void twentyFailedTasksPersistFailureAndGraceDeadlineWithoutBlockingThePoll() {
        List<EciTaskEntity> tasks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            UUID taskId = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();
            String cloudId = "failed-cloud-" + index;
            tasks.add(saveTask(taskId, instanceId));
            saveInstance(instanceId, taskId, cloudId);
            when(provider.describe(cloudId, null)).thenReturn(
                    new CloudInstanceView(cloudId, "Failed", "exit 1"));
            when(provider.fetchContainerLog(cloudId, properties.getFailureLogTailLines(), null))
                    .thenReturn("log tail " + index);
        }

        assertTimeout(Duration.ofSeconds(2), watcher::pollOnce);

        Instant expectedDeleteAt = NOW.plusSeconds(properties.getFailureLogGraceSec());
        for (int index = 0; index < tasks.size(); index++) {
            EciTaskEntity task = taskRepository.findById(tasks.get(index).getId()).orElseThrow();
            assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(task.getContainerLogTail()).isEqualTo("log tail " + index);

            EciInstanceEntity instance = instanceRepository.findById(task.getInstanceId()).orElseThrow();
            assertThat(instance.getStatus()).isEqualTo(InstanceStatus.STOPPING);

            LifecycleActionEntity action = actionRepository.findByInstanceIdAndActionType(
                    task.getInstanceId(), LifecycleActionType.DELETE).orElseThrow();
            assertThat(action.getPhase()).isEqualTo(LifecycleActionPhase.DELETE_REQUEST);
            assertThat(action.getStatus()).isEqualTo(LifecycleActionStatus.READY);
            assertThat(action.getAttemptCount()).isZero();
            assertThat(action.getNextAttemptAt()).isEqualTo(expectedDeleteAt);
        }
        assertThat(clock.instant()).isEqualTo(NOW);
    }

    @Test
    void cloudlessDeleteResolutionPreservesEarlierDeadlineRequestedDuringItsLease() {
        UUID instanceId = UUID.randomUUID();
        saveInstance(instanceId, null, null);

        LifecycleActionEntity create = new LifecycleActionEntity();
        create.setId(UUID.randomUUID());
        create.setInstanceId(instanceId);
        create.setActionType(LifecycleActionType.CREATE);
        create.setPhase(LifecycleActionPhase.CREATE_REQUEST);
        create.setStatus(LifecycleActionStatus.IN_PROGRESS);
        create.setAttemptCount(1);
        create.setNextAttemptAt(NOW);
        create.setLeaseToken(UUID.randomUUID());
        create.setLeaseUntil(NOW.plusSeconds(60));
        create.setCreatedAt(NOW);
        create.setUpdatedAt(NOW);
        actionRepository.saveAndFlush(create);

        actionService.requestDelete(instanceId, NOW);
        LifecycleActionEntity delete = actionRepository.findByInstanceIdAndActionType(
                instanceId, LifecycleActionType.DELETE).orElseThrow();
        UUID leaseToken = actionService.claim(delete.getId(), NOW).orElseThrow().leaseToken();
        Instant claimedUpdatedAt = actionRepository.findById(delete.getId()).orElseThrow().getUpdatedAt();

        actionService.requestDelete(instanceId, NOW.minusSeconds(60));
        actionService.resolveDeleteWithoutCloud(
                instanceId, delete.getId(), leaseToken, claimedUpdatedAt, NOW.plusSeconds(1));

        LifecycleActionEntity waiting = actionRepository.findById(delete.getId()).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(waiting.getNextAttemptAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void legacyNoMarkerNonterminalInstancesSynthesizeUnknownCreateBeforeCloudlessDelete() {
        for (InstanceStatus legacyStatus : List.of(
                InstanceStatus.CREATING,
                InstanceStatus.RUNNING,
                InstanceStatus.STOPPING,
                InstanceStatus.FAILED)) {
            UUID instanceId = UUID.randomUUID();
            saveInstance(instanceId, null, null, legacyStatus);

            actionService.requestDelete(instanceId, NOW);

            LifecycleActionEntity create = actionRepository.findByInstanceIdAndActionType(
                    instanceId, LifecycleActionType.CREATE).orElse(null);
            assertThat(create).as("legacy instance must have durable CREATE evidence")
                    .isNotNull();
            assertThat(create.getPhase()).isEqualTo(LifecycleActionPhase.CREATE_REQUEST);
            assertThat(create.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
            assertThat(create.getAttemptCount()).isEqualTo(1);
            assertThat(create.getClientToken()).isEqualTo(instanceId.toString());
            assertThat(create.getLeaseToken()).isNull();
            assertThat(create.getLeaseUntil()).isNull();

            LifecycleActionEntity delete = actionRepository.findByInstanceIdAndActionType(
                    instanceId, LifecycleActionType.DELETE).orElseThrow();
            UUID leaseToken = actionService.claim(delete.getId(), NOW).orElseThrow().leaseToken();
            processor.process(delete.getId(), leaseToken);

            assertThat(actionRepository.findById(delete.getId()).orElseThrow().getStatus())
                    .isEqualTo(LifecycleActionStatus.WAITING);
            EciInstanceEntity instance = instanceRepository.findById(instanceId).orElseThrow();
            assertThat(instance.getStatus()).isEqualTo(InstanceStatus.STOPPING);
            assertThat(instance.getCloudInstanceId()).isNull();
        }
    }


    private EciTaskEntity saveTask(UUID taskId, UUID instanceId) {
        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setInstanceId(instanceId);
        task.setName("failed-task");
        task.setStatus(TaskStatus.RUNNING);
        task.setImage("nginx:alpine");
        task.setCpu(1);
        task.setMemory(2);
        task.setRestartPolicy("Never");
        task.setMaxRuntimeSec(600);
        task.setEnvJson("{}");
        task.setStartedAt(NOW);
        task.setCreatedAt(NOW);
        task.setUpdatedAt(NOW);
        return taskRepository.saveAndFlush(task);
    }

    private void saveInstance(UUID instanceId, UUID taskId, String cloudId) {
        saveInstance(instanceId, taskId, cloudId, InstanceStatus.RUNNING);
    }

    private void saveInstance(UUID instanceId, UUID taskId, String cloudId, InstanceStatus status) {
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(instanceId);
        instance.setTaskId(taskId);
        instance.setName("failed-instance");
        instance.setImage("nginx:alpine");
        instance.setCpu(1);
        instance.setMemory(2);
        instance.setStatus(status);
        instance.setCloudInstanceId(cloudId);
        instance.setCloudStatus("Running");
        instance.setEnvJson("{}");
        instance.setCreatedAt(NOW);
        instance.setUpdatedAt(NOW);
        instanceRepository.saveAndFlush(instance);
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
