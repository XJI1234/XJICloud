package com.xjicloud.eci.lifecycle.action;

import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.CloudOperationException;
import com.xjicloud.eci.provider.CloudOperationKind;
import com.xjicloud.eci.provider.CreateSpec;
import com.xjicloud.eci.provider.EciProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.lifecycle-action.dispatch-interval-ms=3600000",
        "spring.datasource.url=jdbc:h2:mem:eci-create-processor;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@ExtendWith(OutputCaptureExtension.class)
class CreateLifecycleActionProcessorTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Autowired
    private LifecycleActionRepository actionRepository;
    @SpyBean
    private LifecycleActionService actionService;
    @Autowired
    private LifecycleActionProcessor processor;
    @Autowired
    private EciInstanceRepository instanceRepository;
    @Autowired
    private EciProperties properties;

    @MockBean
    private EciProvider provider;

    @Test
    void createRequestRebuildsPersistedSpecAndWaitsForRunning() {
        UUID instanceId = saveInstance(InstanceStatus.CREATING, null, NOW);
        UUID actionId = saveCreate(instanceId, LifecycleActionPhase.CREATE_REQUEST,
                LifecycleActionStatus.READY, 0, NOW, null);
        when(provider.create(any(CreateSpec.class), eq(instanceId.toString()))).thenReturn("eci-created");

        processAt(actionId, NOW);

        LifecycleActionEntity action = actionRepository.findById(actionId).orElseThrow();
        EciInstanceEntity instance = instanceRepository.findById(instanceId).orElseThrow();
        assertThat(action.getPhase()).isEqualTo(LifecycleActionPhase.WAIT_RUNNING);
        assertThat(action.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(instance.getCloudInstanceId()).isEqualTo("eci-created");
        assertThat(instance.getCloudStatus()).isEqualTo("Pending");
        verify(provider).create(new CreateSpec("persisted-name", "persisted:image", 3.0, 7.0,
                Map.of("FROM", "DATABASE"), "ecs.gn7", 1, "OnFailure",
                List.of("python"), List.of("run.py"), "snapshot-1", "1Gi"), instanceId.toString());
    }

    @Test
    void unknownCreateOutcomeRetriesWithOnlyTheOriginalToken() {
        UUID instanceId = saveInstance(InstanceStatus.CREATING, null, NOW);
        UUID actionId = saveCreate(instanceId, LifecycleActionPhase.CREATE_REQUEST,
                LifecycleActionStatus.READY, 0, NOW, null);
        when(provider.create(any(CreateSpec.class), eq(instanceId.toString())))
                .thenThrow(new CloudOperationException("CreateContainerGroup", "Timeout", CloudOperationKind.UNKNOWN_OUTCOME))
                .thenReturn("eci-one");

        processAt(actionId, NOW);
        LifecycleActionEntity retry = actionRepository.findById(actionId).orElseThrow();
        assertThat(retry.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(retry.getAttemptCount()).isEqualTo(1);

        processAt(actionId, retry.getNextAttemptAt());

        verify(provider, org.mockito.Mockito.times(2)).create(any(CreateSpec.class), eq(instanceId.toString()));
        assertThat(actionRepository.findById(actionId).orElseThrow().getPhase())
                .isEqualTo(LifecycleActionPhase.WAIT_RUNNING);
    }

    @Test
    void legacyUnknownCreateMarkerStaysQuarantinedAndNeverCreatesAnotherCloudInstance() {
        UUID instanceId = saveInstance(InstanceStatus.STOPPING, null, NOW);
        UUID actionId = saveCreate(instanceId, LifecycleActionPhase.CREATE_REQUEST,
                LifecycleActionStatus.WAITING, 1, NOW, "Legacy CREATE outcome unknown");

        processAt(actionId, NOW);

        LifecycleActionEntity action = actionRepository.findById(actionId).orElseThrow();
        assertThat(action.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(action.getLastError()).isEqualTo(LifecycleActionService.LEGACY_CREATE_QUARANTINED);
        verify(provider, never()).create(any(CreateSpec.class), any(String.class));
    }

    @Test
    void legacyUnknownCreateQuarantineAlertsOnceAndKeepsMarkerAfterRetry(CapturedOutput output) {
        UUID instanceId = saveInstance(InstanceStatus.STOPPING, null, NOW);
        UUID actionId = saveCreate(instanceId, LifecycleActionPhase.CREATE_REQUEST,
                LifecycleActionStatus.WAITING, 1, NOW, "Legacy CREATE outcome unknown");

        processAt(actionId, NOW);
        LifecycleActionEntity first = actionRepository.findById(actionId).orElseThrow();
        processAt(actionId, first.getNextAttemptAt());

        LifecycleActionEntity action = actionRepository.findById(actionId).orElseThrow();
        assertThat(action.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(action.getLastError()).startsWith("Legacy CREATE outcome unknown");
        assertThat(output.getOut()).containsOnlyOnce("legacy CREATE quarantined");
        assertThat(output.getOut()).contains("actionId=" + actionId);
        assertThat(output.getOut()).contains("instanceId=" + instanceId);
        assertThat(output.getOut()).doesNotContain("AK=");
        verify(provider, never()).create(any(CreateSpec.class), any(String.class));
    }

    @Test
    void rejectedCreateMarksActionAndBusinessInstanceFailed() {
        UUID instanceId = saveInstance(InstanceStatus.CREATING, null, NOW);
        UUID actionId = saveCreate(instanceId, LifecycleActionPhase.CREATE_REQUEST,
                LifecycleActionStatus.READY, 0, NOW, null);
        when(provider.create(any(CreateSpec.class), eq(instanceId.toString())))
                .thenThrow(new CloudOperationException("CreateContainerGroup", "InvalidParameter", CloudOperationKind.REJECTED));

        processAt(actionId, NOW);

        assertThat(actionRepository.findById(actionId).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.FAILED);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus())
                .isEqualTo(InstanceStatus.FAILED);
    }

    @Test
    void cancellationBeforeCreateCancelsMarkerWithoutCallingProvider() {
        UUID instanceId = saveInstance(InstanceStatus.STOPPING, null, NOW);
        UUID actionId = saveCreate(instanceId, LifecycleActionPhase.CREATE_REQUEST,
                LifecycleActionStatus.READY, 0, NOW, null);

        processAt(actionId, NOW);

        assertThat(actionRepository.findById(actionId).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.CANCELLED);
        verify(provider, never()).create(any(CreateSpec.class), any(String.class));
    }

    @Test
    void cancelledLeaseBetweenInitialCheckAndMarkerPreventsProviderCreate() {
        UUID instanceId = saveInstance(InstanceStatus.CREATING, null, NOW);
        UUID actionId = saveCreate(instanceId, LifecycleActionPhase.CREATE_REQUEST,
                LifecycleActionStatus.READY, 0, NOW, null);
        UUID leaseToken = actionService.claim(actionId, NOW).orElseThrow().leaseToken();
        doAnswer(invocation -> {
            LifecycleActionEntity cancelled = actionRepository.findById(actionId).orElseThrow();
            cancelled.setStatus(LifecycleActionStatus.CANCELLED);
            cancelled.setLeaseToken(null);
            cancelled.setLeaseUntil(null);
            actionRepository.saveAndFlush(cancelled);
            return invocation.callRealMethod();
        }).when(actionService).markCreateCallStarted(actionId, leaseToken);

        processor.process(actionId, leaseToken);

        assertThat(actionRepository.findById(actionId).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.CANCELLED);
        verify(provider, never()).create(any(CreateSpec.class), any(String.class));
    }

    @Test
    void cancellationDuringCreatePersistsCloudIdAndWaitsForDeleteConvergence() {
        UUID instanceId = saveInstance(InstanceStatus.CREATING, null, NOW);
        UUID actionId = saveCreate(instanceId, LifecycleActionPhase.CREATE_REQUEST,
                LifecycleActionStatus.READY, 0, NOW, null);
        doAnswer(invocation -> {
            EciInstanceEntity cancelled = instanceRepository.findById(instanceId).orElseThrow();
            cancelled.setStatus(InstanceStatus.STOPPING);
            instanceRepository.saveAndFlush(cancelled);
            return "eci-accepted-during-cancel";
        }).when(provider).create(any(CreateSpec.class), eq(instanceId.toString()));

        processAt(actionId, NOW);

        LifecycleActionEntity action = actionRepository.findById(actionId).orElseThrow();
        LifecycleActionEntity delete = actionRepository.findByInstanceIdAndActionType(instanceId, LifecycleActionType.DELETE)
                .orElseThrow();
        assertThat(action.getPhase()).isEqualTo(LifecycleActionPhase.WAIT_RUNNING);
        assertThat(action.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus()).isEqualTo(InstanceStatus.STOPPING);
        assertThat(delete.getStatus()).isEqualTo(LifecycleActionStatus.READY);
    }

    @Test
    void mismatchedHistoricalTokenIsRejectedWithoutProviderCall() {
        UUID instanceId = saveInstance(InstanceStatus.CREATING, null, NOW);
        UUID actionId = saveCreate(instanceId, LifecycleActionPhase.CREATE_REQUEST,
                LifecycleActionStatus.READY, 0, NOW, null);
        LifecycleActionEntity stale = actionRepository.findById(actionId).orElseThrow();
        stale.setClientToken(UUID.randomUUID().toString());
        actionRepository.saveAndFlush(stale);

        processAt(actionId, NOW);

        assertThat(actionRepository.findById(actionId).orElseThrow().getStatus())
                .isEqualTo(LifecycleActionStatus.FAILED);
        verify(provider, never()).create(any(CreateSpec.class), any(String.class));
    }

    @Test
    void createTimeoutFailsBusinessButKeepsActionWaitingAndDeletesLateCloudId() {
        UUID instanceId = saveInstance(InstanceStatus.CREATING, null,
                NOW.minusSeconds(properties.getCreateTimeoutSec() + 1L));
        UUID actionId = saveCreate(instanceId, LifecycleActionPhase.CREATE_REQUEST,
                LifecycleActionStatus.READY, 0, NOW.minusSeconds(properties.getCreateTimeoutSec() + 1L), null);
        when(provider.create(any(CreateSpec.class), eq(instanceId.toString())))
                .thenThrow(new CloudOperationException("CreateContainerGroup", "Timeout", CloudOperationKind.UNKNOWN_OUTCOME))
                .thenReturn("eci-late");

        processAt(actionId, NOW);

        EciInstanceEntity instance = instanceRepository.findById(instanceId).orElseThrow();
        LifecycleActionEntity action = actionRepository.findById(actionId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(InstanceStatus.FAILED);
        assertThat(instance.getFailureReason()).contains("等待 Running 超时");
        assertThat(action.getStatus()).isEqualTo(LifecycleActionStatus.WAITING);
        assertThat(action.getPhase()).isEqualTo(LifecycleActionPhase.CREATE_REQUEST);

        processAt(actionId, action.getNextAttemptAt());

        LifecycleActionEntity delete = actionRepository.findByInstanceIdAndActionType(instanceId, LifecycleActionType.DELETE)
                .orElseThrow();
        assertThat(instanceRepository.findById(instanceId).orElseThrow().getStatus()).isEqualTo(InstanceStatus.STOPPING);
        assertThat(actionRepository.findById(actionId).orElseThrow().getPhase())
                .isEqualTo(LifecycleActionPhase.WAIT_RUNNING);
        assertThat(delete.getStatus()).isEqualTo(LifecycleActionStatus.READY);
    }

    @Test
    void waitRunningCompletesOnRunningAndCloudFailureRequestsGraceDelete() {
        UUID runningId = saveInstance(InstanceStatus.CREATING, "eci-running", NOW);
        UUID runningAction = saveCreate(runningId, LifecycleActionPhase.WAIT_RUNNING,
                LifecycleActionStatus.READY, 1, NOW, null);
        when(provider.describe("eci-running")).thenReturn(new CloudInstanceView("eci-running", "Running", null));

        processAt(runningAction, NOW);

        assertThat(instanceRepository.findById(runningId).orElseThrow().getStatus()).isEqualTo(InstanceStatus.RUNNING);
        assertThat(actionRepository.findById(runningAction).orElseThrow().getStatus()).isEqualTo(LifecycleActionStatus.SUCCEEDED);

        UUID failedId = saveInstance(InstanceStatus.CREATING, "eci-failed", NOW);
        UUID failedAction = saveCreate(failedId, LifecycleActionPhase.WAIT_RUNNING,
                LifecycleActionStatus.READY, 1, NOW, null);
        when(provider.describe("eci-failed")).thenReturn(new CloudInstanceView("eci-failed", "Failed", "exit 1"));

        processAt(failedAction, NOW);

        assertThat(actionRepository.findById(failedAction).orElseThrow().getStatus()).isEqualTo(LifecycleActionStatus.SUCCEEDED);
        LifecycleActionEntity delete = actionRepository.findByInstanceIdAndActionType(failedId, LifecycleActionType.DELETE)
                .orElseThrow();
        assertThat(delete.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(properties.getFailureLogGraceSec()));
    }

    private void processAt(UUID actionId, Instant now) {
        UUID leaseToken = actionService.claim(actionId, now).orElseThrow().leaseToken();
        processor.process(actionId, leaseToken);
    }

    private UUID saveInstance(InstanceStatus status, String cloudId, Instant createdAt) {
        UUID id = UUID.randomUUID();
        EciInstanceEntity instance = new EciInstanceEntity();
        instance.setId(id);
        instance.setName("persisted-name");
        instance.setImage("persisted:image");
        instance.setCpu(3.0);
        instance.setMemory(7.0);
        instance.setStatus(status);
        instance.setCloudInstanceId(cloudId);
        instance.setEnvJson("{\"FROM\":\"DATABASE\"}");
        instance.setCommandJson("[\"python\"]");
        instance.setArgsJson("[\"run.py\"]");
        instance.setInstanceType("ecs.gn7");
        instance.setGpu(1);
        instance.setImageSnapshotId("snapshot-1");
        instance.setShmSize("1Gi");
        instance.setRestartPolicy("OnFailure");
        instance.setCreatedAt(createdAt);
        instance.setUpdatedAt(createdAt);
        instanceRepository.saveAndFlush(instance);
        return id;
    }

    private UUID saveCreate(UUID instanceId, LifecycleActionPhase phase,
            LifecycleActionStatus status, int attempts, Instant createdAt, String lastError) {
        LifecycleActionEntity action = new LifecycleActionEntity();
        action.setId(UUID.randomUUID());
        action.setInstanceId(instanceId);
        action.setActionType(LifecycleActionType.CREATE);
        action.setPhase(phase);
        action.setStatus(status);
        action.setClientToken(instanceId.toString());
        action.setAttemptCount(attempts);
        action.setNextAttemptAt(NOW);
        action.setLastError(lastError);
        action.setCreatedAt(createdAt);
        action.setUpdatedAt(NOW);
        actionRepository.saveAndFlush(action);
        return action.getId();
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
