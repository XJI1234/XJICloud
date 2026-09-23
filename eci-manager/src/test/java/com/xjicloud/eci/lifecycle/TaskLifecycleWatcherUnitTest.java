package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.EciProperties;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TaskLifecycleWatcher#pollOnce} 分支：restart policy、持久成功证据、TIMEOUT、先抓日志再删。
 */
@ExtendWith(MockitoExtension.class)
class TaskLifecycleWatcherUnitTest {

    @Mock
    private EciTaskService taskService;
    @Mock
    private EciInstanceService instanceService;
    @Mock
    private EciProvider eciProvider;

    private EciProperties properties;
    private Clock clock;
    private TaskLifecycleWatcher watcher;

    @BeforeEach
    void setUp() {
        properties = new EciProperties();
        properties.setFailureLogTailLines(500);
        properties.setFailureLogGraceSec(0);
        clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        watcher = new TaskLifecycleWatcher(taskService, instanceService, eciProvider, properties, clock);
    }

    @Test
    void neverSucceededWhileRunningSchedulesDeleteWithoutMarkSucceeded() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-1";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Succeeded", null));

        watcher.pollOnce();

        verify(instanceService).recordCloudSucceededAndDeleteForTask(instanceId);
        verify(taskService, never()).markSucceeded(any());
        verify(taskService, never()).onInstanceFailed(any(), any(), any());
        verify(eciProvider, never()).fetchContainerLog(anyString(), anyInt());
    }

    @Test
    void neverSucceededThenTerminatedMarksSucceededOnSecondPoll() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-2";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");
        InstanceResponse terminated = instance(instanceId, taskId, cloudId, InstanceStatus.TERMINATED, "Succeeded");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running, terminated);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Succeeded", null));

        watcher.pollOnce();
        verify(instanceService).recordCloudSucceededAndDeleteForTask(instanceId);
        verify(taskService, never()).markSucceeded(any());

        watcher.pollOnce();
        verify(taskService).markSucceeded(taskId);
    }

    @Test
    void neverSucceededDoesNotMarkSucceededFromInlineCheckEvenIfDeleteLooksTerminated() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-2b";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");
        InstanceResponse terminated = instance(instanceId, taskId, cloudId, InstanceStatus.TERMINATED, "Deleted");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running, terminated);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Succeeded", null));

        watcher.pollOnce();

        verify(instanceService).recordCloudSucceededAndDeleteForTask(instanceId);
        verify(taskService, never()).markSucceeded(any());
    }

    @Test
    void startingWithoutStartedAtDoesNotTimeoutFromCreatedAt() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-6b";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 1, null);
        task.setStatus(TaskStatus.STARTING);
        task.setCreatedAt(Instant.now().minusSeconds(120));
        InstanceResponse creating = instance(instanceId, taskId, cloudId, InstanceStatus.CREATING, "Pending");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(creating);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Pending", null));

        watcher.pollOnce();

        verify(taskService, never()).onInstanceFailed(any(), any(), any());
        verify(instanceService, never()).deleteForTaskCancel(any(), any());
    }

    @Test
    void neverFailedFetchesLogBeforeDelete() {
        properties.setFailureLogGraceSec(90);
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-3";
        String logTail = "train failed\nOOM";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Failed", "exit 1"));
        when(eciProvider.fetchContainerLog(cloudId, 500)).thenReturn(logTail);
        when(instanceService.deleteForTaskCancel(instanceId, clock.instant().plusSeconds(90)))
                .thenReturn(running);

        watcher.pollOnce();

        InOrder order = inOrder(eciProvider, taskService, instanceService);
        order.verify(eciProvider).describe(cloudId);
        order.verify(eciProvider).fetchContainerLog(cloudId, 500);
        order.verify(taskService).onInstanceFailed(taskId, "exit 1", logTail);
        order.verify(instanceService).deleteForTaskCancel(instanceId, clock.instant().plusSeconds(90));
        verify(taskService, never()).markSucceeded(any());
    }

    @Test
    void failedTasksRecordDeleteWithoutWaitingForFailureLogGrace() throws Exception {
        properties.setFailureLogGraceSec(90);
        List<EciTaskEntity> tasks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            UUID taskId = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();
            String cloudId = "mock-failed-" + index;
            tasks.add(activeTask(taskId, instanceId, "Never", 60, Instant.now()));
            when(instanceService.get(instanceId)).thenReturn(
                    instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running"));
            when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Failed", "exit 1"));
            when(eciProvider.fetchContainerLog(cloudId, 500)).thenReturn("failure log");
        }
        when(taskService.findActiveForWatch()).thenReturn(tasks);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread pollThread = new Thread(() -> {
            try {
                watcher.pollOnce();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        pollThread.start();
        pollThread.join(TimeUnit.SECONDS.toMillis(2));
        boolean completedWithoutGraceWait = !pollThread.isAlive();
        if (pollThread.isAlive()) {
            pollThread.interrupt();
            pollThread.join(TimeUnit.SECONDS.toMillis(5));
        }

        assertFalse(pollThread.isAlive(), "poll thread should finish after cleanup");
        assertTrue(completedWithoutGraceWait, "pollOnce must not wait for the configured grace");
        assertNull(failure.get());
        for (EciTaskEntity task : tasks) {
            verify(taskService).onInstanceFailed(eq(task.getId()), eq("exit 1"), eq("failure log"));
            verify(instanceService).deleteForTaskCancel(
                    task.getInstanceId(), clock.instant().plusSeconds(90));
        }
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

        verify(instanceService, never()).deleteForTaskCancel(any(), any());
        verify(taskService, never()).markSucceeded(any());
        verify(taskService, never()).onInstanceFailed(any(), any(), any());
        verify(taskService, never()).onInstanceRunning(any());
    }

    @Test
    void onFailureIgnoresCloudFailedWithoutMarkingRunning() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-5";

        EciTaskEntity task = activeTask(taskId, instanceId, "OnFailure", 60, Instant.now());
        task.setStatus(TaskStatus.STARTING);
        task.setStartedAt(null);
        InstanceResponse creating = instance(instanceId, taskId, cloudId, InstanceStatus.CREATING, "Pending");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(creating);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Failed", "boom"));

        watcher.pollOnce();

        verify(taskService, never()).onInstanceFailed(any(), any(), any());
        verify(instanceService, never()).deleteForTaskCancel(any(), any());
        verify(taskService, never()).onInstanceRunning(any());
        verify(eciProvider, never()).fetchContainerLog(anyString(), anyInt());
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

        verify(taskService, never()).onInstanceFailed(any(), any(), any());
        verify(instanceService, never()).deleteForTaskCancel(any(), any());
        verify(taskService, never()).onInstanceRunning(any());
    }

    @Test
    void timeoutFetchesLogBeforeDelete() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-6";
        String logTail = "still training...";

        Instant started = Instant.now().minusSeconds(10);
        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 1, started);
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running);
        when(eciProvider.fetchContainerLog(cloudId, 500)).thenReturn(logTail);
        when(instanceService.deleteForTaskCancel(instanceId, clock.instant())).thenReturn(running);

        watcher.pollOnce();

        InOrder order = inOrder(eciProvider, taskService, instanceService);
        order.verify(eciProvider).fetchContainerLog(cloudId, 500);
        order.verify(taskService).onInstanceFailed(taskId, "TIMEOUT", logTail);
        order.verify(instanceService).deleteForTaskCancel(instanceId, clock.instant());
        verify(eciProvider, never()).describe(anyString());
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
        verify(eciProvider, never()).fetchContainerLog(anyString(), anyInt());
    }

    @Test
    void terminatedWithPersistedSucceededMarksSucceededWithoutInMemoryLatch() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-7b";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse terminated = instance(instanceId, taskId, cloudId, InstanceStatus.TERMINATED, "Succeeded");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(terminated);

        watcher.pollOnce();

        verify(taskService).markSucceeded(taskId);
        verify(taskService, never()).onInstanceFailed(any(), any(), any());
    }

    @Test
    void terminatedWithoutPersistedSuccessDoesNotBecomeSucceededAfterCloudObservation() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-restarted-watcher";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");
        InstanceResponse terminatedAsDeleted = instance(
                instanceId, taskId, cloudId, InstanceStatus.TERMINATED, "Deleted");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running, terminatedAsDeleted);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Succeeded", null));

        watcher.pollOnce();
        watcher.pollOnce();

        verify(taskService).onInstanceFailed(taskId, "进程中断或资源已清理");
        verify(taskService, never()).markSucceeded(any());
    }

    @Test
    void neverSucceededPersistsCloudSucceededBeforeDelete() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-2c";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Succeeded", null));

        watcher.pollOnce();

        verify(instanceService).recordCloudSucceededAndDeleteForTask(instanceId);
        verify(taskService, never()).markSucceeded(any());
    }

    @Test
    void invalidMaxRuntimeSecDoesNotTimeout() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-6c";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 0, Instant.now().minusSeconds(120));
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Running", null));

        watcher.pollOnce();

        verify(taskService, never()).onInstanceFailed(any(), any(), any());
        verify(instanceService, never()).deleteForTaskCancel(any(), any());
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
        verify(taskService, never()).onInstanceFailed(any(), any(), any());
        verify(instanceService, never()).deleteForTaskCancel(any(), any());
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
        verifyNoMoreInteractions(eciProvider);
    }

    @Test
    void fetchLogFailureStillDeletes() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String cloudId = "mock-eci-9";

        EciTaskEntity task = activeTask(taskId, instanceId, "Never", 60, Instant.now());
        InstanceResponse running = instance(instanceId, taskId, cloudId, InstanceStatus.RUNNING, "Running");

        when(taskService.findActiveForWatch()).thenReturn(List.of(task));
        when(instanceService.get(instanceId)).thenReturn(running);
        when(eciProvider.describe(cloudId)).thenReturn(new CloudInstanceView(cloudId, "Failed", null));
        when(eciProvider.fetchContainerLog(cloudId, 500)).thenThrow(new RuntimeException("log gone"));
        when(instanceService.deleteForTaskCancel(instanceId, clock.instant())).thenReturn(running);

        watcher.pollOnce();

        verify(taskService).onInstanceFailed(taskId, "云侧状态: Failed", null);
        verify(instanceService).deleteForTaskCancel(instanceId, clock.instant());
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
