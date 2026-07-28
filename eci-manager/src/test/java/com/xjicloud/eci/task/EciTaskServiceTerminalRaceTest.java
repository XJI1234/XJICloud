package com.xjicloud.eci.task;

import com.xjicloud.eci.instance.EciInstanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 终态条件更新：FAILED/SUCCEEDED 不得覆盖 CANCELLED。
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
    void onInstanceFailedDoesNotOverwriteCancelled() {
        UUID taskId = seedRunningTask();
        when(instanceService.deleteForTaskCancel(any())).thenReturn(null);
        taskService.cancel(taskId);

        taskService.onInstanceFailed(taskId, "TIMEOUT");

        assertThat(repository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void markSucceededDoesNotOverwriteCancelled() {
        UUID taskId = seedRunningTask();
        when(instanceService.deleteForTaskCancel(any())).thenReturn(null);
        taskService.cancel(taskId);

        taskService.markSucceeded(taskId);

        assertThat(repository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.CANCELLED);
    }

    private UUID seedRunningTask() {
        UUID taskId = UUID.randomUUID();
        EciTaskEntity entity = new EciTaskEntity();
        entity.setId(taskId);
        entity.setName("race");
        entity.setStatus(TaskStatus.RUNNING);
        entity.setImage("nginx:alpine");
        entity.setCpu(1.0);
        entity.setMemory(2.0);
        entity.setRestartPolicy("Never");
        entity.setMaxRuntimeSec(60);
        entity.setInstanceId(UUID.randomUUID());
        entity.setStartedAt(Instant.now());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        return taskId;
    }
}
