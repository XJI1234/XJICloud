package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskService;
import com.xjicloud.eci.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void terminatedInstanceFailsTask() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        EciTaskEntity task = runningTask(taskId, instanceId);

        when(instanceService.listIdsForStartupCompensation()).thenReturn(List.of());
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(
                instance(instanceId, taskId, InstanceStatus.TERMINATED, null));

        new StartupCompensationRunner(instanceService, taskService)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(taskService).onInstanceFailed(taskId, "进程中断或资源已清理");
    }

    @Test
    void failedInstanceFailsTaskWithReason() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        EciTaskEntity task = runningTask(taskId, instanceId);

        when(instanceService.listIdsForStartupCompensation()).thenReturn(List.of());
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(
                instance(instanceId, taskId, InstanceStatus.FAILED, "create boom"));

        new StartupCompensationRunner(instanceService, taskService)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(taskService).onInstanceFailed(taskId, "create boom");
    }

    @Test
    void runningInstanceDeferredToWatcher() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        EciTaskEntity task = runningTask(taskId, instanceId);

        when(instanceService.listIdsForStartupCompensation()).thenReturn(List.of());
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(
                instance(instanceId, taskId, InstanceStatus.RUNNING, null));

        new StartupCompensationRunner(instanceService, taskService)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(taskService, never()).onInstanceFailed(any(), any());
        verify(taskService, never()).markSucceeded(any());
    }

    @Test
    void missingInstanceIdFailsTask() {
        UUID taskId = UUID.randomUUID();
        EciTaskEntity task = runningTask(taskId, null);

        when(instanceService.listIdsForStartupCompensation()).thenReturn(List.of());
        when(taskService.findNonTerminalForCompensation()).thenReturn(List.of(task));

        new StartupCompensationRunner(instanceService, taskService)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(taskService).onInstanceFailed(taskId, "关联实例不存在");
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
            UUID id, UUID taskId, InstanceStatus status, String failureReason) {
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
                status.name(),
                Collections.emptyMap(),
                failureReason,
                now,
                now);
    }
}
