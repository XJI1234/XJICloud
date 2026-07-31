package com.xjicloud.eci.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * storage 接入：OSS 完整配置时 test 模式注入 env。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.poll-interval-ms=200",
        "eci.mock-auto-complete-sec=0",
        "eci.oss.enabled=true",
        "eci.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
        "eci.oss.region=cn-hangzhou",
        "eci.oss.bucket=test-bucket",
        "eci.oss.access-key=test-ak",
        "eci.oss.secret-key=test-sk",
        "spring.datasource.url=jdbc:h2:mem:eci-task-storage-enabled-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class EciTaskStorageEnabledApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EciTaskRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitWithStorageWhenOssConfigured() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\",\"storage\":{\"mode\":\"test\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();

        String taskIdStr = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
        UUID taskId = UUID.fromString(taskIdStr);

        EciTaskEntity entity = repository.findById(taskId).orElseThrow();
        Map<String, String> env = objectMapper.readValue(
                entity.getEnvJson(), new TypeReference<>() {});

        assertThat(env.get("XJI_OUTPUT_OSS_KEY"))
                .isEqualTo("eci-test/" + taskId + "/output.txt");
        assertThat(env.get("XJI_OUTPUT_UPLOAD_URL")).isNotBlank();
        assertThat(env.get("XJI_OUTPUT_UPLOAD_URL")).contains("https://");
        assertThat(env.get("XJI_OSS_BUCKET")).isEqualTo("test-bucket");
    }
}
