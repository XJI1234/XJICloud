package com.xjicloud.eci.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/eci/meta：鉴权、默认规格、OSS 开关；响应不含 endpoint/bucket/密钥。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.region-id=cn-hangzhou",
        "ECI_REGION_ID=cn-hangzhou",
        "eci.default-image=nginx:alpine",
        "ECI_DEFAULT_IMAGE=nginx:alpine",
        "eci.default-cpu=1.0",
        "eci.default-memory=2.0",
        "eci.default-shm-size=256Mi",
        "ECI_DEFAULT_SHM_SIZE=256Mi",
        "eci.default-restart-policy=Never",
        "eci.default-max-runtime-sec=14400",
        "eci.task-poll-interval-sec=10",
        "eci.oss.enabled=false",
        "ECI_OSS_ENABLED=false",
        "eci.oss.endpoint=https://oss-secret-endpoint.example",
        "ECI_OSS_ENDPOINT=https://oss-secret-endpoint.example",
        "eci.oss.bucket=secret-bucket-name",
        "ECI_OSS_BUCKET=secret-bucket-name",
        "eci.oss.access-key=LTAImetaoss01",
        "eci.oss.secret-key=meta-oss-secret-value",
        "eci.console.open-browser=false",
        "spring.datasource.url=jdbc:h2:mem:eci-console-meta;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class EciMetaApiTest {

    private static final String PATH = "/api/v1/eci/meta";
    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsMissingApiKey() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无效或缺失的 Api-Key"));
    }

    @Test
    void rejectsWrongApiKey() throws Exception {
        mockMvc.perform(get(PATH).header("X-Api-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void returnsConsoleDefaultsWithoutSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get(PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mock").value(true))
                .andExpect(jsonPath("$.data.regionId").value("cn-hangzhou"))
                .andExpect(jsonPath("$.data.defaultImage").value("nginx:alpine"))
                .andExpect(jsonPath("$.data.defaultCpu").value(1.0))
                .andExpect(jsonPath("$.data.defaultMemory").value(2.0))
                .andExpect(jsonPath("$.data.defaultShmSize").value("256Mi"))
                .andExpect(jsonPath("$.data.defaultRestartPolicy").value("Never"))
                .andExpect(jsonPath("$.data.defaultMaxRuntimeSec").value(14400))
                .andExpect(jsonPath("$.data.ossEnabled").value(false))
                .andExpect(jsonPath("$.data.ossConfigured").value(true))
                .andExpect(jsonPath("$.data.taskPollIntervalSec").value(10))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("oss-secret-endpoint.example");
        assertThat(body).doesNotContain("secret-bucket-name");
        assertThat(body).doesNotContain("LTAImetaoss01");
        assertThat(body).doesNotContain("meta-oss-secret-value");
        assertThat(body).doesNotContain("accessKeySecret");
        assertThat(body).doesNotContain("secretKey");
    }
}
