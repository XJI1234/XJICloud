package com.xjicloud.eci.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.provider.MockEciProvider;
import com.xjicloud.eci.task.TaskStatus;
import com.xjicloud.eci.task.dto.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mock 下任务终态轮询：Succeeded/Failed 自动清、TIMEOUT、与 cancel 竞态。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.poll-interval-ms=200",
        "eci.task-poll-interval-sec=1",
        "spring.datasource.url=jdbc:h2:mem:eci-task-watcher-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class TaskLifecycleWatcherTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EciProvider eciProvider;

    @Test
    void succeededAutoDeletesAndMarksTaskSucceeded() throws Exception {
        String taskId = submitTask("{\"image\":\"nginx:alpine\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":60}");
        awaitTaskStatus(taskId, TaskStatus.RUNNING);

        String cloudId = cloudInstanceId(taskId);
        mockProvider().simulateStatus(cloudId, "Succeeded");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            TaskResponse task = getTask(taskId);
            assertThat(task.status()).isEqualTo(TaskStatus.SUCCEEDED);
            assertThat(task.endedAt()).isNotNull();
            assertThat(instanceStatus(task.instanceId())).isEqualTo(InstanceStatus.TERMINATED);
        });
    }

    @Test
    void failedAutoDeletesAndMarksTaskFailed() throws Exception {
        String taskId = submitTask("{\"image\":\"nginx:alpine\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":60}");
        awaitTaskStatus(taskId, TaskStatus.RUNNING);

        String cloudId = cloudInstanceId(taskId);
        mockProvider().simulateStatus(cloudId, "Failed");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            TaskResponse task = getTask(taskId);
            assertThat(task.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(task.failureReason()).isNotBlank();
            assertThat(instanceStatus(task.instanceId())).isEqualTo(InstanceStatus.TERMINATED);
        });
    }

    @Test
    void timeoutForceDeletesAndMarksFailed() throws Exception {
        String taskId = submitTask("{\"image\":\"nginx:alpine\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":1}");
        awaitTaskStatus(taskId, TaskStatus.RUNNING);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            TaskResponse task = getTask(taskId);
            assertThat(task.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(task.failureReason()).isEqualTo("TIMEOUT");
            assertThat(instanceStatus(task.instanceId())).isEqualTo(InstanceStatus.TERMINATED);
        });
    }

    @Test
    void cancelWinsOverSucceededRace() throws Exception {
        String taskId = submitTask("{\"image\":\"nginx:alpine\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":60}");
        awaitTaskStatus(taskId, TaskStatus.RUNNING);

        String cloudId = cloudInstanceId(taskId);

        mockMvc.perform(post("/api/v1/eci/tasks/" + taskId + "/cancel")
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // 取消后云侧仍可能上报 Succeeded；不得覆盖 CANCELLED，也不得 500
        mockProvider().simulateStatus(cloudId, "Succeeded");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            TaskResponse task = getTask(taskId);
            assertThat(task.status()).isEqualTo(TaskStatus.CANCELLED);
            assertThat(instanceStatus(task.instanceId()))
                    .isIn(InstanceStatus.STOPPING, InstanceStatus.TERMINATED);
        });
    }

    private MockEciProvider mockProvider() {
        assertThat(eciProvider).isInstanceOf(MockEciProvider.class);
        return (MockEciProvider) eciProvider;
    }

    private String submitTask(String body) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    private void awaitTaskStatus(String taskId, TaskStatus expected) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(getTask(taskId).status()).isEqualTo(expected));
    }

    private TaskResponse getTask(String taskId) throws Exception {
        MvcResult getResult = mockMvc.perform(get("/api/v1/eci/tasks/" + taskId)
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.treeToValue(
                objectMapper.readTree(getResult.getResponse().getContentAsString()).path("data"),
                TaskResponse.class);
    }

    private String cloudInstanceId(String taskId) throws Exception {
        TaskResponse task = getTask(taskId);
        MvcResult getResult = mockMvc.perform(get("/api/v1/eci/instances/" + task.instanceId())
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andReturn();
        String cloudId = objectMapper.readTree(getResult.getResponse().getContentAsString())
                .path("data").path("cloudInstanceId").asText();
        assertThat(cloudId).isNotBlank();
        return cloudId;
    }

    private InstanceStatus instanceStatus(UUID instanceId) throws Exception {
        MvcResult getResult = mockMvc.perform(get("/api/v1/eci/instances/" + instanceId)
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andReturn();
        String status = objectMapper.readTree(getResult.getResponse().getContentAsString())
                .path("data").path("status").asText();
        return InstanceStatus.valueOf(status);
    }
}
