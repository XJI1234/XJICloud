package com.xjicloud.eci.instance;

import com.xjicloud.eci.lifecycle.action.LifecycleActionEntity;
import com.xjicloud.eci.lifecycle.action.LifecycleActionPhase;
import com.xjicloud.eci.lifecycle.action.LifecycleActionRepository;
import com.xjicloud.eci.lifecycle.action.LifecycleActionService;
import com.xjicloud.eci.lifecycle.action.LifecycleActionStatus;
import com.xjicloud.eci.lifecycle.action.LifecycleActionType;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.task-poll-interval-sec=3600",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:eci-delete-termination-race;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class EciInstanceDeleteTerminationRaceIT {

    @Autowired
    private EciInstanceService instanceService;

    @Autowired
    private LifecycleActionService lifecycleActionService;

    @Autowired
    private EciInstanceRepository instanceRepository;

    @Autowired
    private LifecycleActionRepository actionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentCompletionAndDeleteCannotRestoreStoppingAfterTermination() throws Exception {
        UUID instanceId = saveInstance();
        UUID actionId = saveDeleteAction(instanceId);
        UUID leaseToken = actionRepository.findById(actionId).orElseThrow().getLeaseToken();

        CountDownLatch completionHasLock = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        AtomicReference<Thread> deleteThread = new AtomicReference<>();

        TransactionTemplate completionTransaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> completion = executor.submit(() -> {
                Thread.currentThread().setName("completion-race");
                completionTransaction.executeWithoutResult(status -> {
                    instanceRepository.findForUpdateById(instanceId).orElseThrow();
                    completionHasLock.countDown();
                    await(allowCompletion, "completion release");
                    lifecycleActionService.completeInstanceTermination(instanceId, actionId, leaseToken);
                });
            });

            assertThat(completionHasLock.await(5, TimeUnit.SECONDS)).isTrue();
            Future<InstanceResponse> deletion = executor.submit(() -> {
                Thread.currentThread().setName("delete-race");
                deleteThread.set(Thread.currentThread());
                deleteStarted.countDown();
                return instanceService.delete(instanceId);
            });
            assertThat(deleteStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitBlocked(deleteThread.get())).isTrue();
            allowCompletion.countDown();

            completion.get(5, TimeUnit.SECONDS);
            assertThat(deletion.get(5, TimeUnit.SECONDS).status()).isEqualTo(InstanceStatus.TERMINATED);
        } finally {
            allowCompletion.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.TERMINATED);
        assertThat(actionRepository.findById(actionId).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.SUCCEEDED);
    }

    private UUID saveInstance() {
        UUID id = UUID.randomUUID();
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(id);
        instance.setName("delete-race");
        instance.setImage("nginx:alpine");
        instance.setCpu(1);
        instance.setMemory(2);
        instance.setStatus(InstanceStatus.STOPPING);
        instance.setCloudInstanceId("eci-delete-race");
        instance.setEnvJson("{}");
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());
        instanceRepository.saveAndFlush(instance);
        return id;
    }

    private UUID saveDeleteAction(UUID instanceId) {
        UUID id = UUID.randomUUID();
        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(id);
        action.setInstanceId(instanceId);
        action.setActionType(LifecycleActionType.DELETE);
        action.setPhase(LifecycleActionPhase.WAIT_DELETED);
        action.setStatus(LifecycleActionStatus.IN_PROGRESS);
        action.setAttemptCount(0);
        action.setNextAttemptAt(Instant.now());
        action.setLeaseToken(UUID.randomUUID());
        action.setLeaseUntil(Instant.now().plusSeconds(60));
        action.setCreatedAt(Instant.now());
        action.setUpdatedAt(Instant.now());
        actionRepository.saveAndFlush(action);
        return id;
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

    private static boolean awaitBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING
                    || state == Thread.State.TIMED_WAITING
                    || state == Thread.State.BLOCKED) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }
}
