package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.config.AsyncConfig;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.EciTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eci-delete-action-processor;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper=OFF"
})
@Import({LifecycleActionService.class, LifecycleActionProcessor.class, AsyncConfig.class,
        DeleteLifecycleActionProcessorTest.FixedClockConfig.class})
class DeleteLifecycleActionProcessorTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Autowired
    private LifecycleActionProcessor processor;
    @Autowired
    private LifecycleActionService actionService;
    @Autowired
    private LifecycleActionRepository actionRepository;
    @Autowired
    private EciInstanceRepository instanceRepository;
    @Autowired
    private EciProperties properties;

    @MockBean
    private EciProvider provider;

    @MockBean
    private EciTaskService taskService;

    @Test
    void missingCloudAndMissingCreateActionCreateUnknownMarkerAndWait() {
        UUID instanceId = saveInstance(null, InstanceStatus.STOPPING);
        UUID actionId = saveDelete(instanceId, LifecycleActionPhase.DELETE_REQUEST, NOW);

        processAt(actionId, NOW);

        LifecycleActionEntity waitingDelete = actionRepository.findById(actionId).orElseThrow();
        assertThat(waitingDelete.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(waitingDelete.getNextAttemptAt()).isEqualTo(NOW.plusMillis(properties.getPollIntervalMs()));
        LifecycleActionEntity unknownCreate = actionRepository.findByInstanceIdAndActionType(
                instanceId, LifecycleActionType.CREATE).orElseThrow();
        assertThat(unknownCreate.getPhase()).isEqualTo(LifecycleActionPhase.CREATE_REQUEST);
        assertThat(unknownCreate.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(unknownCreate.getAttemptCount()).isEqualTo(1);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);
        verify(provider, never()).delete(org.mockito.ArgumentMatchers.anyString());
        verify(provider, never()).describe(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void missingCloudAndInFlightCreateWaitsForCreate() {
        UUID instanceId = saveInstance(null, InstanceStatus.STOPPING);
        UUID actionId = saveDelete(instanceId, LifecycleActionPhase.DELETE_REQUEST, NOW);
        UUID createId = saveCreate(instanceId, LifecycleActionStatus.IN_PROGRESS, 1);

        processAt(actionId, NOW);

        LifecycleActionEntity waitingDelete = actionRepository.findById(actionId).orElseThrow();
        assertThat(waitingDelete.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(waitingDelete.getNextAttemptAt()).isEqualTo(NOW.plusMillis(properties.getPollIntervalMs()));
        assertThat(actionRepository.findById(createId).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.IN_PROGRESS);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);
        verify(provider, never()).delete(org.mockito.ArgumentMatchers.anyString());
        verify(provider, never()).describe(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void missingCloudAndUncertainCreateResultWaitsForCreate() {
        UUID instanceId = saveInstance(null, InstanceStatus.STOPPING);
        UUID actionId = saveDelete(instanceId, LifecycleActionPhase.DELETE_REQUEST, NOW);
        UUID createId = saveCreate(instanceId, LifecycleActionStatus.WAITING, 1);

        processAt(actionId, NOW);

        LifecycleActionEntity waitingDelete = actionRepository.findById(actionId).orElseThrow();
        assertThat(waitingDelete.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(actionRepository.findById(createId).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);
        verify(provider, never()).delete(org.mockito.ArgumentMatchers.anyString());
        verify(provider, never()).describe(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void missingCloudAndUnstartedCreateIsCancelledBeforeLocalTermination() {
        UUID instanceId = saveInstance(null, InstanceStatus.STOPPING);
        UUID actionId = saveDelete(instanceId, LifecycleActionPhase.DELETE_REQUEST, NOW);
        UUID createId = saveCreate(instanceId, LifecycleActionStatus.READY, 0);

        processAt(actionId, NOW);

        assertCompleted(actionId, instanceId);
        LifecycleActionEntity cancelledCreate = actionRepository.findById(createId).orElseThrow();
        assertThat(cancelledCreate.getStatus()).isEqualTo(LifecycleActionStatus.CANCELLED);
        assertThat(cancelledCreate.getCompletedAt()).isEqualTo(NOW);
        verify(provider, never()).delete(org.mockito.ArgumentMatchers.anyString());
        verify(provider, never()).describe(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void successfulDeleteOnlyMovesToWaitDeleted() {
        UUID instanceId = saveInstance("eci-delete", InstanceStatus.STOPPING);
        UUID actionId = saveDelete(instanceId, LifecycleActionPhase.DELETE_REQUEST, NOW);

        processAt(actionId, NOW);

        LifecycleActionEntity action = actionRepository.findById(actionId).orElseThrow();
        assertThat(action.getPhase()).isEqualTo(LifecycleActionPhase.WAIT_DELETED);
        assertThat(action.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(action.getNextAttemptAt()).isEqualTo(NOW.plusMillis(properties.getPollIntervalMs()));
        assertThat(action.getAttemptCount()).isZero();
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);
        verify(provider).delete("eci-delete");
        verify(provider, never()).describe("eci-delete");
    }

    @Test
    void notFoundDuringWaitDeletedCompletesActionAndInstance() {
        UUID instanceId = saveInstance("eci-gone", InstanceStatus.STOPPING);
        UUID actionId = saveDelete(instanceId, LifecycleActionPhase.WAIT_DELETED, NOW);
        when(provider.describe("eci-gone")).thenReturn(new CloudInstanceView("eci-gone", "NotFound", null));

        processAt(actionId, NOW);

        assertCompleted(actionId, instanceId);
        verify(provider).describe("eci-gone");
        verify(provider, never()).delete("eci-gone");
    }

    @Test
    void deletedDuringWaitDeletedCompletesActionAndInstance() {
        UUID instanceId = saveInstance("eci-deleted", InstanceStatus.STOPPING);
        UUID actionId = saveDelete(instanceId, LifecycleActionPhase.WAIT_DELETED, NOW);
        when(provider.describe("eci-deleted"))
                .thenReturn(new CloudInstanceView("eci-deleted", "Deleted", null));

        processAt(actionId, NOW);

        assertCompleted(actionId, instanceId);
    }

    @Test
    void twoDeleteFailuresBackOffAndThirdAttemptContinuesPolling() {
        UUID instanceId = saveInstance("eci-retry", InstanceStatus.STOPPING);
        UUID actionId = saveDelete(instanceId, LifecycleActionPhase.DELETE_REQUEST, NOW);
        doThrow(new IllegalStateException("temporary 1"))
                .doThrow(new IllegalStateException("temporary 2"))
                .doNothing().when(provider).delete("eci-retry");

        processAt(actionId, NOW);
        LifecycleActionEntity firstFailure = actionRepository.findById(actionId).orElseThrow();
        assertThat(firstFailure.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(firstFailure.getAttemptCount()).isEqualTo(1);
        assertThat(firstFailure.getLastError()).isEqualTo("temporary 1");
        assertThat(firstFailure.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(
                properties.getLifecycleAction().getRetryInitialSec()));

        processAt(actionId, firstFailure.getNextAttemptAt());
        LifecycleActionEntity secondFailure = actionRepository.findById(actionId).orElseThrow();
        assertThat(secondFailure.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(secondFailure.getAttemptCount()).isEqualTo(2);

        processAt(actionId, secondFailure.getNextAttemptAt());
        LifecycleActionEntity succeededDeleteCall = actionRepository.findById(actionId).orElseThrow();
        assertThat(succeededDeleteCall.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(succeededDeleteCall.getPhase()).isEqualTo(LifecycleActionPhase.WAIT_DELETED);
        assertThat(succeededDeleteCall.getAttemptCount()).isEqualTo(2);
        assertThat(succeededDeleteCall.getLastError()).isNull();
        verify(provider, org.mockito.Mockito.times(3)).delete("eci-retry");
    }

    @Test
    void repeatedDeleteFailuresNeverEnterFailed() {
        UUID instanceId = saveInstance("eci-permanent", InstanceStatus.STOPPING);
        UUID actionId = saveDelete(instanceId, LifecycleActionPhase.DELETE_REQUEST, NOW);
        doThrow(new IllegalStateException("still unavailable"))
                .when(provider).delete("eci-permanent");
        Instant due = NOW;

        for (int attempt = 1; attempt <= 8; attempt++) {
            processAt(actionId, due);
            LifecycleActionEntity waiting = actionRepository.findById(actionId).orElseThrow();
            assertThat(waiting.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
            assertThat(waiting.getAttemptCount()).isEqualTo(attempt);
            assertThat(waiting.getLastError()).isEqualTo("still unavailable");
            due = waiting.getNextAttemptAt();
        }
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);
    }

    @Test
    void staleLeaseCannotCommitDeleteResult() {
        UUID instanceId = saveInstance("eci-stale", InstanceStatus.STOPPING);
        UUID actionId = saveDelete(instanceId, LifecycleActionPhase.DELETE_REQUEST, NOW);
        UUID oldLease = actionService.claim(actionId, NOW).orElseThrow().leaseToken();
        LifecycleActionEntity action = actionRepository.findById(actionId).orElseThrow();
        action.setLeaseUntil(NOW);
        actionRepository.saveAndFlush(action);
        UUID newLease = actionService.claim(actionId, NOW.plusSeconds(1)).orElseThrow().leaseToken();
        doNothing().when(provider).delete("eci-stale");

        processor.process(actionId, oldLease);

        LifecycleActionEntity stillClaimed = actionRepository.findById(actionId).orElseThrow();
        assertThat(stillClaimed.getStatus()).isEqualTo(LifecycleActionStatus.IN_PROGRESS);
        assertThat(stillClaimed.getLeaseToken()).isEqualTo(newLease);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);
    }

    @Test
    void waitDeletedTimeoutOnlyMarksWarningAndKeepsRetrying() {
        UUID instanceId = saveInstance("eci-slow-delete", InstanceStatus.STOPPING);
        UUID actionId = saveDelete(instanceId, LifecycleActionPhase.WAIT_DELETED,
                NOW.minusSeconds(properties.getDeleteTimeoutSec() + 1L));
        when(provider.describe("eci-slow-delete"))
                .thenReturn(new CloudInstanceView("eci-slow-delete", "Running", null));

        processAt(actionId, NOW);

        LifecycleActionEntity waiting = actionRepository.findById(actionId).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(waiting.getPhase()).isEqualTo(LifecycleActionPhase.WAIT_DELETED);
        assertThat(waiting.getLastError()).contains("DELETE_TIMEOUT");
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.STOPPING);
    }

    private void processAt(UUID actionId, Instant now) {
        ClaimedLifecycleAction claimed = actionService.claim(actionId, now).orElseThrow();
        processor.process(actionId, claimed.leaseToken());
    }

    private UUID saveInstance(String cloudId, InstanceStatus status) {
        UUID id = UUID.randomUUID();
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(id);
        instance.setName("processor-test");
        instance.setImage("nginx:alpine");
        instance.setCpu(1);
        instance.setMemory(2);
        instance.setStatus(status);
        instance.setCloudInstanceId(cloudId);
        instance.setEnvJson("{}");
        instance.setCreatedAt(NOW);
        instance.setUpdatedAt(NOW);
        instanceRepository.saveAndFlush(instance);
        return id;
    }

    private UUID saveDelete(UUID instanceId, LifecycleActionPhase phase, Instant createdAt) {
        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(UUID.randomUUID());
        action.setInstanceId(instanceId);
        action.setActionType(LifecycleActionType.DELETE);
        action.setPhase(phase);
        action.setStatus(LifecycleActionStatus.READY);
        action.setAttemptCount(0);
        action.setNextAttemptAt(NOW);
        action.setCreatedAt(createdAt);
        action.setUpdatedAt(createdAt);
        actionRepository.saveAndFlush(action);
        return action.getId();
    }

    private UUID saveCreate(UUID instanceId, LifecycleActionStatus status, int attemptCount) {
        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(UUID.randomUUID());
        action.setInstanceId(instanceId);
        action.setActionType(LifecycleActionType.CREATE);
        action.setPhase(LifecycleActionPhase.CREATE_REQUEST);
        action.setStatus(status);
        action.setAttemptCount(attemptCount);
        action.setNextAttemptAt(NOW);
        action.setCreatedAt(NOW);
        action.setUpdatedAt(NOW);
        actionRepository.saveAndFlush(action);
        return action.getId();
    }

    private void assertCompleted(UUID actionId, UUID instanceId) {
        LifecycleActionEntity completed = actionRepository.findById(actionId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(LifecycleActionStatus.SUCCEEDED);
        assertThat(completed.getCompletedAt()).isEqualTo(NOW);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.TERMINATED);
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClockConfig {

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
