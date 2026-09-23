package com.xjicloud.eci.instance;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:eci-legacy-cancel-lock-order;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=1000"
})
class EciInstanceLegacyCancellationLockOrderIT {

    @Autowired
    private EciInstanceService instanceService;
    @Autowired
    private LifecycleActionProcessor processor;
    @Autowired
    private EciTaskRepository taskRepository;
    @Autowired
    private EciInstanceRepository instanceRepository;
    @Autowired
    private LifecycleActionRepository actionRepository;
    @SpyBean
    private LifecycleActionService actionService;
    @SpyBean
    private EciTaskService taskService;
    @MockBean
    private EciProvider provider;

    @Test
    void legacyCancellationTakesTaskLockBeforeDeleteCompletionWithoutDeadlock() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-21T12:00:00Z");
        saveTask(taskId, instanceId, now);
        saveInstance(taskId, instanceId, now);
        saveDeleteAction(actionId, instanceId, leaseToken, now);
        when(provider.describe("legacy-cancel-cloud"))
                .thenReturn(new CloudInstanceView("legacy-cancel-cloud", "Deleted", null));

        CountDownLatch migrationReachedDelete = new CountDownLatch(1);
        CountDownLatch releaseMigration = new CountDownLatch(1);
        CountDownLatch completionStarted = new CountDownLatch(1);
        CountDownLatch completionReachedTaskLock = new CountDownLatch(1);
        doAnswer(invocation -> {
            migrationReachedDelete.countDown();
            await(releaseMigration, "migration release");
            return invocation.callRealMethod();
        }).when(actionService).requestDelete(eq(instanceId), any(Instant.class));
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            completionReachedTaskLock.countDown();
            return result;
        }).when(taskService).lockForDeleteCompletion(eq(taskId));
        doAnswer(invocation -> {
            completionStarted.countDown();
            return invocation.callRealMethod();
        }).when(actionService).completeInstanceTermination(eq(instanceId), eq(actionId), eq(leaseToken));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> migration = executor.submit(() -> instanceService.migrateLegacyCancelledTask(taskId));
            assertThat(migrationReachedDelete.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> completion = executor.submit(() -> processor.process(actionId, leaseToken));
            assertThat(completionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(completionReachedTaskLock.await(500, TimeUnit.MILLISECONDS))
                    .as("DELETE must not acquire task lock while migration owns it")
                    .isFalse();
            releaseMigration.countDown();

            migration.get(5, TimeUnit.SECONDS);
            completion.get(5, TimeUnit.SECONDS);
        } finally {
            releaseMigration.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.TERMINATED);
        assertThat(actionRepository.findById(actionId).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.SUCCEEDED);
    }

    private void saveTask(UUID taskId, UUID instanceId, Instant now) {
        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setName("legacy-cancel-task");
        task.setStatus(TaskStatus.CANCELLED);
        task.setInstanceId(instanceId);
        task.setImage("nginx:alpine");
        task.setCpu(1.0);
        task.setMemory(2.0);
        task.setRestartPolicy("Never");
        task.setMaxRuntimeSec(600);
        task.setEnvJson("{}");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.saveAndFlush(task);
    }

    private void saveInstance(UUID taskId, UUID instanceId, Instant now) {
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(instanceId);
        instance.setTaskId(taskId);
        instance.setName("legacy-cancel-instance");
        instance.setImage("nginx:alpine");
        instance.setCpu(1.0);
        instance.setMemory(2.0);
        instance.setStatus(InstanceStatus.STOPPING);
        instance.setCloudInstanceId("legacy-cancel-cloud");
        instance.setCloudStatus("Running");
        instance.setEnvJson("{}");
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        instanceRepository.saveAndFlush(instance);
    }

    private void saveDeleteAction(UUID actionId, UUID instanceId, UUID leaseToken, Instant now) {
        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(actionId);
        action.setInstanceId(instanceId);
        action.setActionType(LifecycleActionType.DELETE);
        action.setPhase(LifecycleActionPhase.WAIT_DELETED);
        action.setStatus(LifecycleActionStatus.IN_PROGRESS);
        action.setAttemptCount(0);
        action.setNextAttemptAt(now);
        action.setLeaseToken(leaseToken);
        action.setLeaseUntil(now.plusSeconds(60));
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        actionRepository.saveAndFlush(action);
    }

    private static void await(CountDownLatch latch, String label) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(label + " timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(label + " interrupted", exception);
        }
    }
}
