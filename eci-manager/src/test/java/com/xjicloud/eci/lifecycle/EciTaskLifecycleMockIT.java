package com.xjicloud.eci.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.instance.InstanceStatus;
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
 * Mock 时间触发终态端到端：不调用 simulateStatus，提交 → Running → 自动清 → 任务终态。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.poll-interval-ms=200",
        "eci.task-poll-interval-sec=1",
        "eci.mock-auto-complete-sec=1",
        "eci.mock-auto-complete-status=Succeeded",
        "spring.datasource.url=jdbc:h2:mem:eci-task-mock-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class EciTaskLifecycleMockIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void autoSucceededWithoutSimulateStatus() throws Exception {
        String taskId = submitTask(
                "{\"image\":\"nginx:alpine\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":60}");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(getTask(taskId).status()).isEqualTo(TaskStatus.RUNNING));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            TaskResponse task = getTask(taskId);
            assertThat(task.status()).isEqualTo(TaskStatus.SUCCEEDED);
            assertThat(task.endedAt()).isNotNull();
            assertThat(instanceStatus(task.instanceId())).isEqualTo(InstanceStatus.TERMINATED);
        });
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

    private TaskResponse getTask(String taskId) throws Exception {
        MvcResult getResult = mockMvc.perform(get("/api/v1/eci/tasks/" + taskId)
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.treeToValue(
                objectMapper.readTree(getResult.getResponse().getContentAsString()).path("data"),
                TaskResponse.class);
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
