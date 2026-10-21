package com.xjicloud.eci.task;

import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 任务终态条件更新：取消不能覆盖 FAILED/SUCCEEDED 的业务终态。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "spring.datasource.url=jdbc:h2:mem:eci-task-terminal-race;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class EciTaskServiceTerminalRaceTest {

    @Autowired
    private EciTaskService taskService;

    @Autowired
    private EciTaskRepository repository;

    @MockBean
    private EciInstanceService instanceService;

    @Test
    void onInstanceFailedDoesNotOverwriteCancelling() {
        UUID taskId = seedRunningTask();
        when(instanceService.deleteForTaskCancel(any())).thenReturn(stoppingInstanceResponse(taskId));
        taskService.cancel(taskId);

        taskService.onInstanceFailed(taskId, "TIMEOUT");

        assertThat(repository.findById(taskId).orElseThrow().getStatus().name())
                .isEqualTo("CANCELLING");
    }

    @Test
    void markSucceededDoesNotOverwriteCancelling() {
        UUID taskId = seedRunningTask();
        when(instanceService.deleteForTaskCancel(any())).thenReturn(stoppingInstanceResponse(taskId));
        taskService.cancel(taskId);

        taskService.markSucceeded(taskId);

        assertThat(repository.findById(taskId).orElseThrow().getStatus().name())
                .isEqualTo("CANCELLING");
    }

    @Test
    void succeededTaskCannotBeCancelled() {
        UUID taskId = seedTask(TaskStatus.SUCCEEDED);

        assertThatThrownBy(() -> taskService.cancel(taskId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void failedTaskCancellationIsIdempotentAndPreservesTerminalEvidence() {
        UUID instanceId = UUID.randomUUID();
        UUID taskId = seedTask(TaskStatus.FAILED, instanceId);
        EciTaskEntity failed = repository.findById(taskId).orElseThrow();
        failed.setFailureReason("provider failure");
        failed.setContainerLogTail("last container output");
        repository.saveAndFlush(failed);
        Instant endedAt = failed.getEndedAt();
        when(instanceService.deleteForTaskCancel(instanceId)).thenReturn(stoppingInstanceResponse(taskId));

        assertThat(taskService.cancel(taskId).status()).isEqualTo(TaskStatus.FAILED);
        assertThat(taskService.cancel(taskId).status()).isEqualTo(TaskStatus.FAILED);

        EciTaskEntity unchanged = repository.findById(taskId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(unchanged.getFailureReason()).isEqualTo("provider failure");
        assertThat(unchanged.getContainerLogTail()).isEqualTo("last container output");
        assertThat(unchanged.getEndedAt()).isEqualTo(endedAt);
    }

    @Test
    void taskWithoutInstanceCannotBeCancelled() {
        UUID taskId = seedTask(TaskStatus.STARTING, null);

        assertThatThrownBy(() -> taskService.cancel(taskId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);

        EciTaskEntity task = repository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.STARTING);
        assertThat(task.getEndedAt()).isNull();
    }

    private UUID seedRunningTask() {
        return seedTask(TaskStatus.RUNNING);
    }

    private UUID seedTask(TaskStatus status) {
        return seedTask(status, UUID.randomUUID());
    }

    private UUID seedTask(TaskStatus status, UUID instanceId) {
        UUID taskId = UUID.randomUUID();
        EciTaskEntity entity = new EciTaskEntity();
        entity.setId(taskId);
        entity.setName("race");
        entity.setStatus(status);
        entity.setImage("nginx:alpine");
        entity.setCpu(1.0);
        entity.setMemory(2.0);
        entity.setRestartPolicy("Never");
        entity.setMaxRuntimeSec(60);
        entity.setInstanceId(instanceId);
        if (status == TaskStatus.RUNNING) {
            entity.setStartedAt(Instant.now());
        } else if (status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED) {
            entity.setEndedAt(Instant.now());
        }
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        return taskId;
    }

    private InstanceResponse stoppingInstanceResponse(UUID taskId) {
        EciTaskEntity task = repository.findById(taskId).orElseThrow();
        Instant now = Instant.now();
        return new InstanceResponse(
                task.getInstanceId(), taskId, "race", "nginx:alpine", 1.0, 2.0,
                InstanceStatus.STOPPING, "race-cloud-id", "Running", Map.of(), null, now, now);
    }
}
