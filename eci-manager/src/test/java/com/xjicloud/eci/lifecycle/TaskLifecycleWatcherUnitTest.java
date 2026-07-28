package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.provider.CloudInstanceView;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.task.EciTaskEntity;
import com.xjicloud.eci.task.EciTaskService;
import com.xjicloud.eci.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TaskLifecycleWatcher#pollOnce} 分支：Never/Always/OnFailure、SUCCEEDED 门闩、TIMEOUT。
 */
@ExtendWith(MockitoExtension.class)
class TaskLifecycleWatcherUnitTest {

    @Mock
    private EciTaskService taskService;
    @Mock
    private EciInstanceService instanceService;
    @Mock
    private EciProvider eciProvider;

    private TaskLifecycleWatcher watcher;

    @BeforeEach
    void setUp() {
        watcher = new TaskLifecycleWatcher(taskService, instanceService, eciProvider);
    }

    @Test
    void neverSucceededWhileRunningSchedulesDeleteWithoutMarkSucceeded() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-1";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");
        InstanceResponse stopping = instance(instanceId, taskId, cloudId, InstanceStatus.STOPPING, "Succeeded");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running, stopping);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Succeeded", null));
        when(instanceService.deleteForTaskCancel(instanceId)).thenReturn(stopping);

        watcher.pollOnce();

        verify(instanceService).deleteForTaskCancel(instanceId);
        verify(taskService, never()).markSucceeded(any());
        verify(taskService, never()).onInstanceFailed(any(), any());
    }

    @Test
    void neverSucceededThenTerminatedMarksSucceededOnSecondPoll() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-2";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");
        InstanceResponse stopping = instance(instanceId, taskId, cloudId, InstanceStatus.STOPPING, "Succeeded");
        InstanceResponse terminated = instance(instanceId, taskId, cloudId, InstanceStatus.TERMINATED, "Deleted");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        // poll1: get=running, safeGet=stopping; poll2: get=terminated
        when(instanceService.get(instanceId)).thenReturn(running, stopping, terminated);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Succeeded", null));
        when(instanceService.deleteForTaskCancel(instanceId)).thenReturn(stopping);

        watcher.pollOnce();
        verify(taskService, never()).markSucceeded(any());

        watcher.pollOnce();
        verify(taskService).markSucceeded(taskId);
    }

    @Test
    void neverFailedMarksFailedAndDeletes() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-3";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Failed", "exit 1"));
        when(instanceService.deleteForTaskCancel(instanceId)).thenReturn(running);

        watcher.pollOnce();

        verify(taskService).onInstanceFailed(taskId, "exit 1");
        verify(instanceService).deleteForTaskCancel(instanceId);
        verify(taskService, never()).markSucceeded(any());
    }

    @Test
    void alwaysIgnoresCloudSucceeded() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-4";

        EciTaskEntity task = activeTask(taskId, instanceId, "Always", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Succeeded", null));

        watcher.pollOnce();

        verify(instanceService, never()).deleteForTaskCancel(any());
        verify(taskService, never()).markSucceeded(any());
        verify(taskService, never()).onInstanceFailed(any(), any());
        verify(taskService, never()).onInstanceRunning(any());
    }

    @Test
    void onFailureIgnoresCloudFailedWithoutMarkingRunning() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-5";

        // STARTING is the harmful case: onInstanceRunning would wrongly promote to RUNNING
        EciTaskEntity task = activeTask(taskId, instanceId, "OnFailure", 60, Instant.now());
        task.setStatus(TaskStatus.STARTING);
        task.setStartedAt(null);
        InstanceResponse creating = instance(instanceId, taskId, cloudId, InstanceStatus.CREATING, "Pending");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(creating);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Failed", "boom"));

        watcher.pollOnce();

        verify(taskService, never()).onInstanceFailed(any(), any());
        verify(instanceService, never()).deleteForTaskCancel(any());
        // Failed + OnFailure: wait for restart / Succeeded / timeout — do not pretend Running
        verify(taskService, never()).onInstanceRunning(any());
    }

    @Test
    void alwaysIgnoresCloudFailedWithoutMarkingRunning() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-5b";

        EciTaskEntity task = activeTask(taskId, instanceId, "Always", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Failed", "boom"));

        watcher.pollOnce();

        verify(taskService, never()).onInstanceFailed(any(), any());
        verify(instanceService, never()).deleteForTaskCancel(any());
        verify(taskService, never()).onInstanceRunning(any());
    }

    @Test
    void timeoutForceDeletesAndFails() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-6";

        Instant started = Instant.now().minusSeconds(10);
        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 1, started);
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running);
        when(instanceService.deleteForTaskCancel(instanceId)).thenReturn(running);

        watcher.pollOnce();

        verify(taskService).onInstanceFailed(taskId, "TIMEOUT");
        verify(instanceService).deleteForTaskCancel(instanceId);
        verifyNoInteractions(eciProvider);
    }

    @Test
    void orphanTerminatedWithoutPendingSucceededFails() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-7";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse terminated = instance(instanceId, taskId, cloudId, InstanceStatus.TERMINATED, "Deleted");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(terminated);

        watcher.pollOnce();

        verify(taskService).onInstanceFailed(taskId, "进程中断或资源已清理");
        verify(taskService, never()).markSucceeded(any());
    }

    @Test
    void describeExceptionDoesNotTerminalize() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-8";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running);
        when(eciProvider.describe(cloudId)).thenThrow(new RuntimeException("cloud blip"));

        watcher.pollOnce();

        verify(taskService, never()).markSucceeded(any());
        verify(taskService, never()).onInstanceFailed(any(), any());
        verify(instanceService, never()).deleteForTaskCancel(any());
    }

    @Test
    void missingInstanceMarksFailed() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId))
                .thenThrow(new BusinessException("实例不存在", HttpStatus.NOT_FOUND));

        watcher.pollOnce();

        verify(taskService).onInstanceFailed(eq(taskId), eq("关联实例不存在"));
    }

    private static EciTaskEntity activeTask(
            UUID taskId, UUID instanceId, String restartPolicy, int maxRuntimeSec, Instant startedAt) {
        EciTaskEntity task = new EciTaskEntity();
        task.setId(taskId);
        task.setInstanceId(instanceId);
        task.setStatus(TaskStatus.RUNNING);
        task.setRestartPolicy(restartPolicy);
        task.setMaxRuntimeSec(maxRuntimeSec);
        task.setStartedAt(startedAt);
        task.setCreatedAt(startedAt != null ? startedAt : Instant.now());
        return task;
    }

    private static InstanceResponse instance(
            UUID id, UUID taskId, String cloudId, InstanceStatus status, String cloudStatus) {
        Instant now = Instant.now();
        return new InstanceResponse(
                id,
                taskId,
                "demo",
                "nginx:alpine",
                1.0,
                2.0,
                status,
                cloudId,
                cloudStatus,
                Collections.emptyMap(),
                null,
                now,
                now);
    }
}
