package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.dto.CreateInstanceRequest;
import com.xjicloud.eci.lifecycle.action.LifecycleActionEntity;
import com.xjicloud.eci.lifecycle.action.LifecycleActionProcessor;
import com.xjicloud.eci.lifecycle.action.LifecycleActionRepository;
import com.xjicloud.eci.lifecycle.action.LifecycleActionService;
import com.xjicloud.eci.lifecycle.action.LifecycleActionStatus;
import com.xjicloud.eci.lifecycle.action.LifecycleActionType;
import com.xjicloud.eci.lifecycle.action.LifecycleActionPhase;
import com.xjicloud.eci.provider.MockEciProvider;
import com.xjicloud.eci.task.EciTaskRepository;
import com.xjicloud.eci.task.EciTaskService;
import com.xjicloud.eci.task.dto.CreateTaskRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:eci-create-recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class LifecycleActionRecoveryIT {

    @Autowired
    private EciInstanceService instanceService;
    @Autowired
    private EciTaskService taskService;
    @Autowired
    private EciInstanceRepository instanceRepository;
    @Autowired
    private EciTaskRepository taskRepository;
    @Autowired
    private LifecycleActionRepository actionRepository;
    @Autowired
    private LifecycleActionService actionService;
    @Autowired
    private LifecycleActionProcessor processor;
    @Autowired
    private MockEciProvider provider;

    @SpyBean
    private LifecycleActionService lifecycleActionSpy;

    @Test
    void manualAndTaskCreatesPersistExactlyOneReadyCreateAction() {
        var manual = instanceService.create(manualRequest());

        CreateTaskRequest taskRequest = new CreateTaskRequest();
        taskRequest.setName("task-create");
        taskRequest.setImage("task:image");
        taskRequest.setEnv(Map.of("TASK", "one"));
        var task = taskService.create(taskRequest);

        assertCreateAction(manual.id());
        assertCreateAction(task.instanceId());
    }

    @Test
    void actionInsertFailureRollsBackManualInstanceAndTaskTransaction() {
        doThrow(new IllegalStateException("action insert failed"))
                .when(lifecycleActionSpy).createCreateAction(any());
        int instancesBefore = instanceRepository.findAll().size();
        int tasksBefore = taskRepository.findAll().size();
        int actionsBefore = actionRepository.findAll().size();

        assertThatThrownBy(() -> instanceService.create(manualRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("action insert failed");
        assertThat(instanceRepository.findAll()).hasSize(instancesBefore);
        assertThat(actionRepository.findAll()).hasSize(actionsBefore);

        CreateTaskRequest taskRequest = new CreateTaskRequest();
        taskRequest.setImage("task:image");
        assertThatThrownBy(() -> taskService.create(taskRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("action insert failed");
        assertThat(taskRepository.findAll()).hasSize(tasksBefore);
        assertThat(instanceRepository.findAll()).hasSize(instancesBefore);
        assertThat(actionRepository.findAll()).hasSize(actionsBefore);
    }

    @Test
    void unknownOutcomeRecoveryUsesOneTokenForOneCloudInstance() {
        int cloudResourcesBefore = provider.getCloudInstanceCount();
        var instance = instanceService.create(manualRequest());
        LifecycleActionEntity action = actionRepository.findByInstanceIdAndActionType(
                instance.id(), LifecycleActionType.CREATE).orElseThrow();
        provider.failNextCreateAfterAccepted(instance.id().toString());

        processAt(action.getId(), Instant.now());
        LifecycleActionEntity retry = actionRepository.findById(action.getId()).orElseThrow();
        assertThat(retry.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);

        processAt(action.getId(), retry.getNextAttemptAt());
        assertThat(provider.getCloudInstanceCount()).isEqualTo(cloudResourcesBefore + 1);

        LifecycleActionEntity waitRunning = actionRepository.findById(action.getId()).orElseThrow();
        processAt(waitRunning.getId(), waitRunning.getNextAttemptAt());
        assertThat(actionRepository.findById(action.getId()).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.SUCCEEDED);
    }

    @Test
    void unknownOutcomeThenCancellationRecoversTheSameTokenAndDeletesTheCloudResource() {
        int cloudResourcesBefore = provider.getCloudInstanceCount();
        var instance = instanceService.create(manualRequest());
        LifecycleActionEntity create = createAction(instance.id());
        provider.failNextCreateAfterAccepted(instance.id().toString());

        processAt(create.getId(), Instant.now());
        instanceService.deleteForTaskCancel(instance.id());

        create = actionRepository.findById(create.getId()).orElseThrow();
        processAt(create.getId(), create.getNextAttemptAt());
        LifecycleActionEntity delete = deleteAction(instance.id());
        processAt(delete.getId(), delete.getNextAttemptAt());
        delete = actionRepository.findById(delete.getId()).orElseThrow();
        processAt(delete.getId(), delete.getNextAttemptAt());

        assertThat(instanceRepository.findById(instance.id()).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.TERMINATED);
        assertThat(provider.getCloudInstanceCount()).isEqualTo(cloudResourcesBefore);
    }

    @Test
    void knownCloudCancellationAndTimeoutConvergeToDeleteWithoutFurtherCreatePolling() {
        var cancelled = instanceService.create(manualRequest());
        LifecycleActionEntity cancelledCreate = createAction(cancelled.id());
        processAt(cancelledCreate.getId(), Instant.now());
        instanceService.deleteForTaskCancel(cancelled.id());

        cancelledCreate = actionRepository.findById(cancelledCreate.getId()).orElseThrow();
        processAt(cancelledCreate.getId(), cancelledCreate.getNextAttemptAt());
        assertThat(actionRepository.findById(cancelledCreate.getId()).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.SUCCEEDED);
        assertThat(deleteAction(cancelled.id()).getStatus()).isEqualTo(LifecycleActionStatus.READY);

        var timedOut = instanceService.create(manualRequest());
        LifecycleActionEntity timeoutCreate = createAction(timedOut.id());
        processAt(timeoutCreate.getId(), timeoutCreate.getNextAttemptAt());
        timeoutCreate = actionRepository.findById(timeoutCreate.getId()).orElseThrow();
        timeoutCreate.setCreatedAt(Instant.now().minusSeconds(601));
        actionRepository.saveAndFlush(timeoutCreate);
        provider.simulateStatus(instanceRepository.findById(timedOut.id()).orElseThrow().getCloudInstanceId(), "Pending");

        processAt(timeoutCreate.getId(), timeoutCreate.getNextAttemptAt());

        assertThat(actionRepository.findById(timeoutCreate.getId()).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.SUCCEEDED);
        assertThat(instanceRepository.findById(timedOut.id()).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);
        assertThat(deleteAction(timedOut.id()).getStatus()).isEqualTo(LifecycleActionStatus.READY);
    }

    @Test
    void createFailureFetchesAndPersistsLogsBeforeGraceDelete() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setName("failed-create");
        request.setImage("task:image");
        var task = taskService.create(request);
        LifecycleActionEntity create = createAction(task.instanceId());
        processAt(create.getId(), Instant.now());
        String cloudId = instanceRepository.findById(task.instanceId()).orElseThrow().getCloudInstanceId();
        provider.setContainerLog(cloudId, "create failure log");
        provider.simulateStatus(cloudId, "Failed");

        create = actionRepository.findById(create.getId()).orElseThrow();
        processAt(create.getId(), create.getNextAttemptAt());

        assertThat(taskRepository.findById(task.id()).orElseThrow().getContainerLogTail())
                .isEqualTo("create failure log");
        assertThat(instanceRepository.findById(task.instanceId()).orElseThrow().getFailureReason())
                .contains("mock exitCode=1");
        LifecycleActionEntity delete = deleteAction(task.instanceId());
        assertThat(provider.getLifecycleCallOrder()).containsExactly("fetch");

        processAt(delete.getId(), delete.getNextAttemptAt());
        assertThat(provider.getLifecycleCallOrder()).containsExactly("fetch", "delete");
    }

    @Test
    void cancellationAndCreateReceiptRaceCompletesWithoutLockOrderFailure() throws Exception {
        var instance = instanceService.create(manualRequest());
        LifecycleActionEntity create = createAction(instance.id());
        var lease = actionService.claim(create.getId(), Instant.now()).orElseThrow();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> cancel = executor.submit(() -> {
                await(start);
                instanceService.deleteForTaskCancel(instance.id());
            });
            Future<?> receipt = executor.submit(() -> {
                await(start);
                actionService.acceptCreate(create.getId(), lease.leaseToken(), "race-cloud");
            });
            start.countDown();
            cancel.get();
            receipt.get();
        } finally {
            executor.shutdownNow();
        }
        assertThat(deleteAction(instance.id()).getStatus()).isEqualTo(LifecycleActionStatus.READY);
    }

    @Test
    void cancellationDoesNotDeadlockAnyCreateStateMutation() throws Exception {
        raceCancellationWithCreateMutation((id, lease) -> actionService.cancelCreateBeforeProviderCall(id, lease));
        raceCancellationWithCreateMutation((id, lease) -> actionService.waitForRunning(id, lease, "Pending"));
        raceCancellationWithCreateMutation((id, lease) -> actionService.completeCreateRunning(id, lease, "Running"));
        raceCancellationWithCreateMutation((id, lease) -> actionService.failCreate(id, lease, "rejected"));
        raceCancellationWithCreateMutation((id, lease) ->
                actionService.completeCreateFailedAndRequestDelete(id, lease, "failed", "log tail"));
        raceCancellationWithCreateMutation((id, lease) -> actionService.timeoutCreateBusiness(id, lease));
    }

    private void raceCancellationWithCreateMutation(BiConsumer<java.util.UUID, java.util.UUID> mutation)
            throws Exception {
        var instance = instanceService.create(manualRequest());
        LifecycleActionEntity create = createAction(instance.id());
        var lease = actionService.claim(create.getId(), Instant.now()).orElseThrow();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> cancel = executor.submit(() -> { await(start); instanceService.deleteForTaskCancel(instance.id()); });
            Future<?> mutate = executor.submit(() -> { await(start); mutation.accept(create.getId(), lease.leaseToken()); });
            start.countDown();
            cancel.get();
            mutate.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private LifecycleActionEntity createAction(java.util.UUID instanceId) {
        return actionRepository.findByInstanceIdAndActionType(instanceId, LifecycleActionType.CREATE).orElseThrow();
    }

    private LifecycleActionEntity deleteAction(java.util.UUID instanceId) {
        return actionRepository.findByInstanceIdAndActionType(instanceId, LifecycleActionType.DELETE).orElseThrow();
    }

    private void assertCreateAction(java.util.UUID instanceId) {
        LifecycleActionEntity action = actionRepository.findByInstanceIdAndActionType(
                instanceId, LifecycleActionType.CREATE).orElseThrow();
        assertThat(action.getStatus()).isEqualTo(LifecycleActionStatus.READY);
        assertThat(action.getClientToken()).isEqualTo(instanceId.toString());
        assertThat(action.getAttemptCount()).isZero();
    }

    private void processAt(java.util.UUID actionId, Instant now) {
        var lease = actionService.claim(actionId, now).orElseThrow();
        processor.process(actionId, lease.leaseToken());
    }

    private static CreateInstanceRequest manualRequest() {
        CreateInstanceRequest request = new CreateInstanceRequest();
        request.setName("manual-create");
        request.setImage("manual:image");
        request.setEnv(Map.of("MANUAL", "one"));
        return request;
    }
}
