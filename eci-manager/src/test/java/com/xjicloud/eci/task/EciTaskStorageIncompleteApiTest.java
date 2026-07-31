package com.xjicloud.eci.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * storage 接入：OSS enabled 但 bucket 为空（isConfigured=false）→ 400，不落库。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.poll-interval-ms=200",
        "eci.mock-auto-complete-sec=0",
        "eci.oss.enabled=true",
        "eci.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
        "eci.oss.region=cn-hangzhou",
        "eci.oss.bucket=",
        "eci.oss.access-key=test-ak",
        "eci.oss.secret-key=test-sk",
        "spring.datasource.url=jdbc:h2:mem:eci-task-storage-incomplete-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class EciTaskStorageIncompleteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EciTaskRepository repository;

    @Test
    void rejectsStorageWhenOssIncomplete() throws Exception {
        long before = repository.count();
        mockMvc.perform(post("/api/v1/eci/tasks")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:alpine\",\"storage\":{\"mode\":\"test\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("OSS")));
        assertThat(repository.count()).isEqualTo(before);
    }
}
