package com.xjicloud.eci.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.task.TaskStatus;
import com.xjicloud.eci.task.dto.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mock 自动终态为 Failed 时的端到端（独立 Spring 上下文）。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.poll-interval-ms=200",
        "eci.task-poll-interval-sec=1",
        "eci.mock-auto-complete-sec=1",
        "eci.mock-auto-complete-status=Failed",
        "spring.datasource.url=jdbc:h2:mem:eci-task-mock-it-failed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class EciTaskLifecycleMockFailedIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void autoFailedWithoutSimulateStatus() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\",\"restartPolicy\":\"Never\",\"maxRuntimeSec\":60}"))
                .andExpect(status().isOk())
                .andReturn();
        String taskId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            MvcResult getResult = mockMvc.perform(get("/api/v1/eci/tasks/" + taskId)
                            .header("X-Api-Key", "test-api-key"))
                    .andExpect(status().isOk())
                    .andReturn();
            TaskResponse task = objectMapper.treeToValue(
                    objectMapper.readTree(getResult.getResponse().getContentAsString()).path("data"),
                    TaskResponse.class);
            assertThat(task.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(task.failureReason()).isNotBlank();

            MvcResult instResult = mockMvc.perform(get("/api/v1/eci/instances/" + task.instanceId())
                            .header("X-Api-Key", "test-api-key"))
                    .andExpect(status().isOk())
                    .andReturn();
            String instStatus = objectMapper.readTree(instResult.getResponse().getContentAsString())
                    .path("data").path("status").asText();
            assertThat(instStatus).isEqualTo("TERMINATED");
        });
    }
}
