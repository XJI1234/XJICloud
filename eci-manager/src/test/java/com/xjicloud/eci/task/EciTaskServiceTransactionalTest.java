package com.xjicloud.eci.task;

import com.xjicloud.eci.instance.EciInstanceService;
import com.xjicloud.eci.task.dto.CreateTaskRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * create/cancel 须与关联实例写操作同事务；下游失败时不得留下半截任务状态。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "spring.datasource.url=jdbc:h2:mem:eci-task-tx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class EciTaskServiceTransactionalTest {

    @Autowired
    private EciTaskService taskService;

    @Autowired
    private EciTaskRepository repository;

    @MockBean
    private EciInstanceService instanceService;

    @Test
    void createRollsBackTaskWhenInstanceCreateFails() {
        when(instanceService.createForTask(any(), any()))
                .thenThrow(new RuntimeException("instance create failed"));

        CreateTaskRequest request = new CreateTaskRequest();
        request.setImage("nginx:alpine");

        assertThatThrownBy(() -> taskService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("instance create failed");

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void cancelRollsBackWhenInstanceDeleteFails() {
        UUID taskId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        EciTaskEntity entity = new EciTaskEntity();
        entity.setId(taskId);
        entity.setName("cancel-tx");
        entity.setStatus(TaskStatus.RUNNING);
        entity.setImage("nginx:alpine");
        entity.setCpu(1.0);
        entity.setMemory(2.0);
        entity.setRestartPolicy("Never");
        entity.setMaxRuntimeSec(60);
        entity.setInstanceId(instanceId);
        entity.setCreatedAt(java.time.Instant.now());
        entity.setUpdatedAt(java.time.Instant.now());
        repository.save(entity);

        when(instanceService.deleteForTaskCancel(instanceId))
                .thenThrow(new RuntimeException("delete failed"));

        assertThatThrownBy(() -> taskService.cancel(taskId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("delete failed");

        EciTaskEntity reloaded = repository.findById(taskId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(reloaded.getEndedAt()).isNull();
    }
}
