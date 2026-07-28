package com.xjicloud.eci.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.instance.InstanceStatus;
import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.provider.EciProvider;
import com.xjicloud.eci.provider.MockEciProvider;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 手动实例：Mock 云侧终态后本地仍 RUNNING，不会被任务 watcher 自动删除。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.poll-interval-ms=200",
        "eci.task-poll-interval-sec=1",
        "eci.mock-auto-complete-sec=1",
        "eci.mock-auto-complete-status=Succeeded",
        "spring.datasource.url=jdbc:h2:mem:eci-manual-no-auto-delete;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ManualInstanceNoAutoDeleteIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EciProvider eciProvider;

    @Test
    void manualInstanceStaysRunningAfterMockCloudSucceeded() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/eci/instances")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"manual-keep\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(nullValue()))
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            InstanceResponse body = getInstance(id);
            assertThat(body.status()).isEqualTo(InstanceStatus.RUNNING);
            assertThat(body.taskId()).isNull();
            assertThat(body.cloudInstanceId()).startsWith("mock-eci-");
        });

        InstanceResponse running = getInstance(id);
        String cloudId = running.cloudInstanceId();
        assertThat(eciProvider).isInstanceOf(MockEciProvider.class);
        // 确保云侧已是 Succeeded（时间触发或钩子均可）
        ((MockEciProvider) eciProvider).simulateStatus(cloudId, "Succeeded");

        mockMvc.perform(post("/api/v1/eci/instances/" + id + "/refresh")
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk());

        // 给 watcher 几轮机会：手动实例无 task，不得被自动删
        await().pollDelay(2, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            InstanceResponse body = getInstance(id);
            assertThat(body.status()).isEqualTo(InstanceStatus.RUNNING);
            assertThat(body.cloudStatus()).isEqualToIgnoringCase("Succeeded");
        });

        mockMvc.perform(delete("/api/v1/eci/instances/" + id)
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(getInstance(id).status()).isEqualTo(InstanceStatus.TERMINATED));
    }

    private InstanceResponse getInstance(String id) throws Exception {
        MvcResult getResult = mockMvc.perform(get("/api/v1/eci/instances/" + id)
                        .header("X-Api-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.treeToValue(
                objectMapper.readTree(getResult.getResponse().getContentAsString()).path("data"),
                InstanceResponse.class);
    }
}
