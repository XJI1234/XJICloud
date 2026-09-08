package com.xjicloud.eci.task;

import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.instance.EciInstanceEntity;
import com.xjicloud.eci.instance.EciInstanceRepository;
import com.xjicloud.eci.instance.InstanceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 冒烟：任务配置默认值绑定，以及 eci_task / eci_instance 扩展字段可持久化。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "spring.datasource.url=jdbc:h2:mem:eci-task-mapping;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class EciTaskEntityMappingTest {

    @Autowired
    private EciProperties properties;

    @Autowired
    private EciTaskRepository taskRepository;

    @Autowired
    private EciInstanceRepository instanceRepository;

    @Test
    void taskRuntimeConfigDefaultsBound() {
        assertThat(properties.getDefaultRestartPolicy()).isEqualTo("Never");
        assertThat(properties.getDefaultMaxRuntimeSec()).isEqualTo(14400);
        assertThat(properties.getTaskPollIntervalSec()).isEqualTo(10);
        assertThat(properties.getFailureLogTailLines()).isEqualTo(1000);
        // test/resources/application.yml 关闭宽限，避免 IT 卡住
        assertThat(properties.getFailureLogGraceSec()).isEqualTo(0);
    }

    @Test
    void persistsContainerLogTail() {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();

        EciTaskEntity task = new EciTaskEntity();
        task.setId(id);
        task.setName("log-tail-task");
        task.setStatus(TaskStatus.FAILED);
        task.setImage("nginx:alpine");
        task.setCpu(1.0);
        task.setMemory(2.0);
        task.setRestartPolicy("Never");
        task.setMaxRuntimeSec(60);
        task.setFailureReason("云侧状态: Failed");
        task.setContainerLogTail("line1\nline2\nOOM");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setEndedAt(now);

        taskRepository.save(task);

        EciTaskEntity loaded = taskRepository.findById(id).orElseThrow();
        assertThat(loaded.getContainerLogTail()).isEqualTo("line1\nline2\nOOM");
        assertThat(loaded.getFailureReason()).isEqualTo("云侧状态: Failed");
    }

    @Test
    void persistsTaskEntity() {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();

        EciTaskEntity task = new EciTaskEntity();
        task.setId(id);
        task.setName("mapping-task");
        task.setStatus(TaskStatus.PENDING);
        task.setImage("nginx:alpine");
        task.setCpu(1.0);
        task.setMemory(2.0);
        task.setGpu(1);
        task.setImageSnapshotId("imc-mapping-test");
        task.setRestartPolicy("Never");
        task.setMaxRuntimeSec(60);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        taskRepository.save(task);

        EciTaskEntity loaded = taskRepository.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(loaded.getImage()).isEqualTo("nginx:alpine");
        assertThat(loaded.getRestartPolicy()).isEqualTo("Never");
        assertThat(loaded.getImageSnapshotId()).isEqualTo("imc-mapping-test");
        assertThat(loaded.getMaxRuntimeSec()).isEqualTo(60);
        assertThat(loaded.getInstanceId()).isNull();
    }

    @Test
    void persistsInstanceWithNullableTaskId() {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();

        EciInstanceEntity entity = new EciInstanceEntity();
        entity.setId(id);
        entity.setName("manual-instance");
        entity.setImage("nginx:alpine");
        entity.setCpu(1.0);
        entity.setMemory(2.0);
        entity.setStatus(InstanceStatus.CREATING);
        entity.setRestartPolicy("Never");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        instanceRepository.save(entity);

        EciInstanceEntity loaded = instanceRepository.findById(id).orElseThrow();
        assertThat(loaded.getTaskId()).isNull();
        assertThat(loaded.getRestartPolicy()).isEqualTo("Never");
    }
}
