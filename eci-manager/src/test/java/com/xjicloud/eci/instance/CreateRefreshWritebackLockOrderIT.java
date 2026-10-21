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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** A real H2 race between refresh and CREATE terminal writeback. */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.task-poll-interval-sec=3600",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:eci-create-refresh-lock-order-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=1000"
})
class CreateRefreshWritebackLockOrderIT {

    private static final Instant CREATED_AT = Instant.parse("2026-10-21T12:00:00Z");
    private static final String REFRESH_THREAD = "t1-refresh";
    private static final String CREATE_THREAD = "t1-create-writeback";

    @Autowired
    private EciInstanceService instanceService;
    @Autowired
    private LifecycleActionService actionService;
    @Autowired
    private EciTaskRepository taskRepository;
    @Autowired
    private EciInstanceRepository instanceRepository;
    @Autowired
    private LifecycleActionRepository actionRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @SpyBean
    private EciTaskService taskService;

    @MockBean
    private EciProvider provider;

    @Test
    void createSuccessWritebackAndRefreshFinishWithRunningInstance() throws Exception {
        Scenario scenario = saveScenario();
        when(provider.describe(scenario.cloudId()))
                .thenReturn(new CloudInstanceView(scenario.cloudId(), "Running", null));

        raceRefreshAndWriteback(scenario, () -> actionService.completeCreateRunning(
                scenario.createActionId(), scenario.leaseToken(), "Running"));

        assertThat(instanceRepository.findById(scenario.instanceId()).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.RUNNING);
        assertThat(taskRepository.findById(scenario.taskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.RUNNING);
        assertThat(actionRepository.findById(scenario.createActionId()).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.SUCCEEDED);
        assertThat(actionRepository.findByInstanceIdAndActionType(
                scenario.instanceId(), LifecycleActionType.DELETE)).isEmpty();
    }

    @Test
    void createFailureWritebackAndRefreshFinishWithFailedInstance() throws Exception {
        Scenario scenario = saveScenario();
        when(provider.describe(scenario.cloudId()))
                .thenReturn(new CloudInstanceView(scenario.cloudId(), "Running", null));

        raceRefreshAndWriteback(scenario, () -> actionService.completeCreateFailedAndRequestDelete(
                scenario.createActionId(), scenario.leaseToken(), "provider rejected create", "create log",
                "Failed", "provider rejected create"));

        EciInstanceEntity instance = instanceRepository.findById(scenario.instanceId()).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.STOPPING);
        assertThat(instance.getFailureReason()).isEqualTo("provider rejected create");
        assertThat(taskRepository.findById(scenario.taskId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.FAILED);
        assertThat(taskRepository.findById(scenario.taskId()).orElseThrow().getContainerLogTail())
                .isEqualTo("create log");
        assertThat(actionRepository.findById(scenario.createActionId()).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.SUCCEEDED);
    }

    private void raceRefreshAndWriteback(Scenario scenario, Runnable createWriteback) throws Exception {
        CountDownLatch gateHasTaskLock = new CountDownLatch(1);
        CountDownLatch releaseGate = new CountDownLatch(1);
        CountDownLatch refreshRequestedTaskLock = new CountDownLatch(1);
        CountDownLatch refreshHasTaskLock = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        CountDownLatch createRequestedTaskLock = new CountDownLatch(1);

        doAnswer(invocation -> {
            String threadName = Thread.currentThread().getName();
            if (threadName.equals(REFRESH_THREAD)) {
                refreshRequestedTaskLock.countDown();
                Object result = invocation.callRealMethod();
                refreshHasTaskLock.countDown();
                await(releaseRefresh, "refresh release");
                return result;
            }
            if (threadName.equals(CREATE_THREAD)) {
                createRequestedTaskLock.countDown();
            }
            return invocation.callRealMethod();
        }).when(taskService).lockForDeleteCompletion(eq(scenario.taskId()));

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Future<?> taskLockGate = executor.submit(() -> transaction.executeWithoutResult(status -> {
                taskRepository.findForUpdateById(scenario.taskId()).orElseThrow();
                gateHasTaskLock.countDown();
                await(releaseGate, "task lock gate release");
            }));
            assertThat(gateHasTaskLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> refresh = executor.submit(() -> withThreadName(REFRESH_THREAD,
                    () -> instanceService.refresh(scenario.instanceId())));
            assertThat(refreshRequestedTaskLock.await(5, TimeUnit.SECONDS)).isTrue();
            releaseGate.countDown();
            assertThat(refreshHasTaskLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> create = executor.submit(() -> withThreadName(CREATE_THREAD, () -> {
                createWriteback.run();
            }));
            assertThat(createRequestedTaskLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> instanceLockProbe = executor.submit(() -> transaction.executeWithoutResult(status ->
                    instanceRepository.findForUpdateById(scenario.instanceId()).orElseThrow()));
            instanceLockProbe.get(5, TimeUnit.SECONDS);
            assertThat(create.isDone()).as("CREATE must wait while refresh owns the task row").isFalse();

            releaseRefresh.countDown();
            taskLockGate.get(5, TimeUnit.SECONDS);
            refresh.get(5, TimeUnit.SECONDS);
            create.get(5, TimeUnit.SECONDS);
        } finally {
            releaseGate.countDown();
            releaseRefresh.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Scenario saveScenario() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();
        String cloudId = "create-refresh-cloud-" + instanceId;

        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setName("create-refresh-task");
        task.setStatus(TaskStatus.STARTING);
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
        instance.setName("create-refresh-instance");
        instance.setImage("nginx:alpine");
        instance.setCpu(1.0);
        instance.setMemory(2.0);
        instance.setStatus(InstanceStatus.CREATING);
        instance.setCloudInstanceId(cloudId);
        instance.setCloudStatus("Pending");
        instance.setEnvJson("{}");
        instance.setCreatedAt(CREATED_AT);
        instance.setUpdatedAt(CREATED_AT);
        instanceRepository.saveAndFlush(instance);

        LifecycleActionEntity create = new LifecycleActionEntity();
        create.setId(actionId);
        create.setInstanceId(instanceId);
        create.setActionType(LifecycleActionType.CREATE);
        create.setPhase(LifecycleActionPhase.WAIT_RUNNING);
        create.setStatus(LifecycleActionStatus.IN_PROGRESS);
        create.setAttemptCount(1);
        create.setNextAttemptAt(CREATED_AT);
        create.setLeaseToken(leaseToken);
        create.setLeaseUntil(CREATED_AT.plusSeconds(60));
        create.setCreatedAt(CREATED_AT);
        create.setUpdatedAt(CREATED_AT);
        actionRepository.saveAndFlush(create);

        return new Scenario(taskId, instanceId, actionId, leaseToken, cloudId);
    }

    private static void withThreadName(String name, Runnable runnable) {
        String originalName = Thread.currentThread().getName();
        Thread.currentThread().setName(name);
        try {
            runnable.run();
        } finally {
            Thread.currentThread().setName(originalName);
        }
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

    private record Scenario(UUID taskId, UUID instanceId, UUID createActionId, UUID leaseToken, String cloudId) {
    }
}
