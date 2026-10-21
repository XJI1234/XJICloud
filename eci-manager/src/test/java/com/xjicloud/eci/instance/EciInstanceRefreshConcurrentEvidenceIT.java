package com.xjicloud.eci.instance;

import com.xjicloud.eci.lifecycle.action.LifecycleActionEntity;
import com.xjicloud.eci.lifecycle.action.LifecycleActionPhase;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.task-poll-interval-sec=3600",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:eci-refresh-concurrent-evidence-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class EciInstanceRefreshConcurrentEvidenceIT {

    private static final Instant CREATED_AT = Instant.parse("2026-10-01T12:00:00Z");
    private static final String SUCCESS_REASON = "trainer completed successfully";

    @Autowired
    private EciInstanceService instanceService;
    @Autowired
    private LifecycleActionService lifecycleActionService;
    @Autowired
    private EciInstanceRepository instanceRepository;
    @Autowired
    private LifecycleActionRepository actionRepository;
    @Autowired
    private EciTaskRepository taskRepository;
    @Autowired
    private EciTaskService taskService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private EciProvider provider;

    @Test
    void staleRefreshCannotOverwriteConcurrentSucceededTermination() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID deleteActionId = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        String cloudId = "concurrent-refresh-cloud-id";
        saveTask(taskId, instanceId);
        saveInstance(taskId, instanceId, cloudId);
        saveDeleteAction(deleteActionId, instanceId, leaseToken);
        EciInstanceEntity beforeRefresh = instanceRepository.findById(instanceId).orElseThrow();
        assertThat(beforeRefresh.getCloudStatus()).isEqualTo("Deleting");
        assertThat(beforeRefresh.hasCloudSucceeded()).isFalse();

        CountDownLatch completionHasTaskLock = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        CountDownLatch refreshReachedProvider = new CountDownLatch(1);
        AtomicReference<Thread> refreshThread = new AtomicReference<>();
        when(provider.describe(cloudId)).thenAnswer(invocation -> {
            refreshReachedProvider.countDown();
            return new CloudInstanceView(cloudId, "NotFound", null);
        });

        TransactionTemplate completionTransaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> completion = executor.submit(() -> completionTransaction.executeWithoutResult(status -> {
                taskRepository.findForUpdateById(taskId).orElseThrow();
                completionHasTaskLock.countDown();
                await(allowCompletion, "completion release");
                EciInstanceEntity succeeded = instanceRepository.findForUpdateById(instanceId).orElseThrow();
                succeeded.setCloudStatus("Succeeded");
                succeeded.setStatusReason(SUCCESS_REASON);
                instanceRepository.save(succeeded);
                assertThat(succeeded.hasCloudSucceeded()).isTrue();
                lifecycleActionService.completeInstanceTermination(instanceId, deleteActionId, leaseToken);
            }));
            assertThat(completionHasTaskLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> refresh = executor.submit(() -> {
                refreshThread.set(Thread.currentThread());
                instanceService.refresh(instanceId);
            });
            assertThat(refreshReachedProvider.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(instanceRepository.findById(instanceId).orElseThrow().hasCloudSucceeded()).isFalse();
            assertThat(awaitBlocked(refreshThread, "refresh waiting for task row lock")).isTrue();

            allowCompletion.countDown();
            completion.get(5, TimeUnit.SECONDS);
            refresh.get(5, TimeUnit.SECONDS);
        } finally {
            allowCompletion.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        EciInstanceEntity afterRefresh = instanceRepository.findById(instanceId).orElseThrow();
        var taskSnapshot = taskService.get(taskId);
        assertThat(taskSnapshot.status()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(taskSnapshot.instanceStatus()).isEqualTo(InstanceStatus.TERMINATED);
        assertThat(taskSnapshot.cleanupConfirmed()).isTrue();
        assertThat(afterRefresh.getStatus()).isEqualTo(InstanceStatus.TERMINATED);
        assertThat(afterRefresh.hasCloudSucceeded()).isTrue();
        assertThat(afterRefresh.getCloudStatus()).isEqualTo("Succeeded");
    }

    private void saveTask(UUID taskId, UUID instanceId) {
        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setName("concurrent-refresh-task");
        task.setStatus(TaskStatus.RUNNING);
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
    }

    private void saveInstance(UUID taskId, UUID instanceId, String cloudId) {
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(instanceId);
        instance.setTaskId(taskId);
        instance.setName("concurrent-refresh-instance");
        instance.setImage("nginx:alpine");
        instance.setCpu(1.0);
        instance.setMemory(2.0);
        instance.setStatus(InstanceStatus.STOPPING);
        instance.setCloudInstanceId(cloudId);
        instance.setCloudStatus("Running");
        instance.setStatusReason(SUCCESS_REASON);
        instance.setEnvJson("{}");
        instance.setCreatedAt(CREATED_AT);
        instance.setUpdatedAt(CREATED_AT);
        instanceRepository.saveAndFlush(instance);
        lifecycleActionService.recordProviderStatus(instanceId, "Deleting", "delete is in progress");
    }

    private void saveDeleteAction(UUID actionId, UUID instanceId, UUID leaseToken) {
        LifecycleActionEntity delete = new LifecycleActionEntity();
        delete.setId(actionId);
        delete.setInstanceId(instanceId);
        delete.setActionType(LifecycleActionType.DELETE);
        delete.setPhase(LifecycleActionPhase.WAIT_DELETED);
        delete.setStatus(LifecycleActionStatus.IN_PROGRESS);
        delete.setAttemptCount(0);
        delete.setNextAttemptAt(CREATED_AT);
        delete.setLeaseToken(leaseToken);
        delete.setLeaseUntil(CREATED_AT.plusSeconds(60));
        delete.setCreatedAt(CREATED_AT);
        delete.setUpdatedAt(CREATED_AT);
        actionRepository.saveAndFlush(delete);
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

    private static boolean awaitBlocked(AtomicReference<Thread> threadReference, String label) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread thread = threadReference.get();
            if (thread != null) {
                Thread.State state = thread.getState();
                if (state == Thread.State.WAITING
                        || state == Thread.State.TIMED_WAITING
                        || state == Thread.State.BLOCKED) {
                    return true;
                }
            }
            Thread.onSpinWait();
        }
        return false;
    }
}
