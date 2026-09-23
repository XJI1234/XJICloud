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
import com.xjicloud.eci.task.dto.TaskResponse;
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

/** Detects DELETE taking instance/action locks before the task row held by cancel. */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "eci.lifecycle-action.lease-sec=60",
        "spring.datasource.url=jdbc:h2:mem:eci-delete-cancel-lock-order;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=1000"
})
class DeleteCancellationLockOrderIT {

    @Autowired
    private LifecycleActionProcessor processor;
    @Autowired
    private LifecycleActionRepository actionRepository;
    @Autowired
    private EciInstanceRepository instanceRepository;
    @Autowired
    private EciTaskRepository taskRepository;
    @SpyBean
    private LifecycleActionService actionService;
    @SpyBean
    private EciInstanceService instanceService;
    @SpyBean
    private EciTaskService taskService;
    @MockBean
    private EciProvider provider;

    @Test
    void cancelWinsBeforeDeleteCompletionWithoutCrossTableDeadlock() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UUID initialLease = UUID.randomUUID();
        String cloudId = "lock-order-cloud-id";
        Instant now = Instant.now();
        saveTask(taskId, instanceId, now);
        saveInstance(taskId, instanceId, cloudId, now);
        saveDeleteAction(actionId, instanceId, initialLease, now);
        when(provider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Deleted", null));

        CountDownLatch cancelHoldsTaskRow = new CountDownLatch(1);
        CountDownLatch releaseCancelBeforeInstance = new CountDownLatch(1);
        CountDownLatch completionStarted = new CountDownLatch(1);
        CountDownLatch completionReachedTaskTransition = new CountDownLatch(1);

        doAnswer(invocation -> {
            cancelHoldsTaskRow.countDown();
            await(releaseCancelBeforeInstance, "cancel release");
            return invocation.callRealMethod();
        }).when(instanceService).deleteForTaskCancel(eq(instanceId));
        doAnswer(invocation -> {
            completionReachedTaskTransition.countDown();
            return invocation.callRealMethod();
        }).when(taskService).onInstanceTerminated(eq(taskId), any());
        doAnswer(invocation -> {
            completionStarted.countDown();
            return invocation.callRealMethod();
        }).when(actionService).completeInstanceTermination(eq(instanceId), eq(actionId), eq(initialLease));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TaskResponse> cancellation = executor.submit(() -> taskService.cancel(taskId));
            assertThat(cancelHoldsTaskRow.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> completion = executor.submit(() -> processor.process(actionId, initialLease));
            assertThat(completionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            boolean completionEnteredTaskTransitionBeforeCancelReleased =
                    completionReachedTaskTransition.await(500, TimeUnit.MILLISECONDS);
            releaseCancelBeforeInstance.countDown();

            assertThat(completionEnteredTaskTransitionBeforeCancelReleased)
                    .as("DELETE must acquire the task row before instance/action locks")
                    .isFalse();
            assertThat(cancellation.get(5, TimeUnit.SECONDS).status()).isEqualTo(TaskStatus.CANCELLING);
            completion.get(5, TimeUnit.SECONDS);
        } finally {
            releaseCancelBeforeInstance.countDown();
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
        task.setName("lock-order-task");
        task.setStatus(TaskStatus.RUNNING);
        task.setInstanceId(instanceId);
        task.setImage("nginx:alpine");
        task.setCpu(1.0);
        task.setMemory(2.0);
        task.setRestartPolicy("Never");
        task.setMaxRuntimeSec(600);
        task.setEnvJson("{}");
        task.setStartedAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.saveAndFlush(task);
    }

    private void saveInstance(UUID taskId, UUID instanceId, String cloudId, Instant now) {
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(instanceId);
        instance.setTaskId(taskId);
        instance.setName("lock-order-instance");
        instance.setImage("nginx:alpine");
        instance.setCpu(1.0);
        instance.setMemory(2.0);
        instance.setStatus(InstanceStatus.STOPPING);
        instance.setCloudInstanceId(cloudId);
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
