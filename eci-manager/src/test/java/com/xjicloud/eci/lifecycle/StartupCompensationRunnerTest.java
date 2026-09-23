package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.lifecycle.action.LifecycleActionService;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskService;
import com.xjicloud.eci.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 启动任务补偿：实例 FAILED/TERMINATED → 任务 FAILED；RUNNING 交给 Watcher。
 */
@ExtendWith(MockitoExtension.class)
class StartupCompensationRunnerTest {

    @Mock
    private EciInstanceService instanceService;
    @Mock
    private EciTaskService taskService;
    @Mock
    private LifecycleActionService lifecycleActionService;

    @Test
    void terminatedInstanceFailsTask() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        EciTaskEntity task = runningTask(taskId, instanceId);

        when(instanceService.findCandidateInstanceIds()).thenReturn(List.of());
        when(instanceService.findLegacyCancelledTaskIds()).thenReturn(List.of());
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(
                instance(instanceId, taskId, InstanceStatus.TERMINATED, "Deleted", null));

        runStartupCompensation();

        verify(taskService).onInstanceFailed(taskId, "进程中断或资源已清理");
    }

    @Test
    void terminatedAfterCloudSucceededMarksTaskSucceeded() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        EciTaskEntity task = runningTask(taskId, instanceId);

        when(instanceService.findCandidateInstanceIds()).thenReturn(List.of());
        when(instanceService.findLegacyCancelledTaskIds()).thenReturn(List.of());
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(
                instance(instanceId, taskId, InstanceStatus.TERMINATED, "Succeeded", null));

        runStartupCompensation();

        verify(taskService).markSucceeded(taskId);
        verify(taskService, never()).onInstanceFailed(any(), any());
    }

    @Test
    void failedInstanceFailsTaskWithReason() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        EciTaskEntity task = runningTask(taskId, instanceId);

        when(instanceService.findCandidateInstanceIds()).thenReturn(List.of());
        when(instanceService.findLegacyCancelledTaskIds()).thenReturn(List.of());
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(
                instance(instanceId, taskId, InstanceStatus.FAILED, "Failed", "create boom"));

        runStartupCompensation();

        verify(taskService).onInstanceFailed(taskId, "create boom");
    }

    @Test
    void runningInstanceDeferredToWatcher() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        EciTaskEntity task = runningTask(taskId, instanceId);

        when(instanceService.findCandidateInstanceIds()).thenReturn(List.of());
        when(instanceService.findLegacyCancelledTaskIds()).thenReturn(List.of());
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(
                instance(instanceId, taskId, InstanceStatus.RUNNING, "Running", null));

        runStartupCompensation();

        verify(taskService, never()).onInstanceFailed(any(), any());
        verify(taskService, never()).markSucceeded(any());
    }

    @Test
    void missingInstanceIdFailsTask() {
        UUID taskId = UUID.randomUUID();
        EciTaskEntity task = runningTask(taskId, null);

        when(instanceService.findCandidateInstanceIds()).thenReturn(List.of());
        when(instanceService.findLegacyCancelledTaskIds()).thenReturn(List.of());
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of(task));

        runStartupCompensation();

        verify(taskService).onInstanceFailed(taskId, "关联实例不存在");
    }

    @Test
    void startupProcessesLeasesInstancesLegacyTasksThenTaskEvidenceInOrder() {
        UUID instanceId = UUID.randomUUID();
        UUID cancelledTaskId = UUID.randomUUID();
        when(instanceService.findCandidateInstanceIds()).thenReturn(List.of(instanceId));
        when(instanceService.findLegacyCancelledTaskIds()).thenReturn(List.of(cancelledTaskId));
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of());

        runStartupCompensation();

        var order = inOrder(lifecycleActionService, instanceService, taskService);
        order.verify(lifecycleActionService).recoverExpiredLeases(any(Instant.class));
        order.verify(instanceService).findCandidateInstanceIds();
        order.verify(instanceService).ensureLegacyActionsForInstance(instanceId);
        order.verify(instanceService).findLegacyCancelledTaskIds();
        order.verify(instanceService).migrateLegacyCancelledTask(cancelledTaskId);
        order.verify(taskService).findNonTerminalForCompensation();
    }

    @Test
    void oneBadInstanceCandidateDoesNotStopLaterRows() {
        UUID broken = UUID.randomUUID();
        UUID valid = UUID.randomUUID();
        when(instanceService.findCandidateInstanceIds()).thenReturn(List.of(broken, valid));
        when(instanceService.findLegacyCancelledTaskIds()).thenReturn(List.of());
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of());
        doThrow(new IllegalStateException("corrupt row"))
                .when(instanceService).ensureLegacyActionsForInstance(broken);

        runStartupCompensation();

        verify(instanceService).ensureLegacyActionsForInstance(valid);
    }

    @Test
    void oneBadLegacyCancellationDoesNotStopLaterRowsInCandidateOrder() {
        UUID mismatched = UUID.randomUUID();
        UUID valid = UUID.randomUUID();
        when(instanceService.findCandidateInstanceIds()).thenReturn(List.of());
        when(instanceService.findLegacyCancelledTaskIds()).thenReturn(List.of(mismatched, valid));
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of());
        doThrow(new IllegalStateException("association mismatch"))
                .when(instanceService).migrateLegacyCancelledTask(mismatched);

        runStartupCompensation();

        var order = inOrder(instanceService);
        order.verify(instanceService).migrateLegacyCancelledTask(mismatched);
        order.verify(instanceService).migrateLegacyCancelledTask(valid);
    }

    private void runStartupCompensation() {
        new StartupCompensationRunner(instanceService, taskService, lifecycleActionService,
                Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC))
                .run(new DefaultApplicationArguments(new String[0]));
    }

    private static EciTaskEntity runningTask(UUID taskId, UUID instanceId) {
        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setInstanceId(instanceId);
        task.setStatus(TaskStatus.RUNNING);
        task.setCreatedAt(Instant.now());
        return task;
    }

    private static InstanceResponse instance(
            UUID id, UUID taskId, InstanceStatus status, String cloudStatus, String failureReason) {
        Instant now = Instant.now();
        return new InstanceResponse(
                id,
                taskId,
                "demo",
                "nginx:alpine",
                1.0,
                2.0,
                status,
                "mock-eci-x",
                cloudStatus,
                Collections.emptyMap(),
                failureReason,
                now,
                now);
    }
}
