package com.xjicloud.eci.credential;

import com.xjicloud.eci.config.OssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /runtime-credentials/test：独立内存库，CI 走 RecordingProbe，不连真云。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.oss.access-key=LTAIseedoss01",
        "eci.oss.secret-key=seed-secret-value",
        "eci.oss.endpoint=https://oss-cn-hangzhou-internal.aliyuncs.com",
        "ECI_OSS_ENDPOINT=https://oss-cn-hangzhou-internal.aliyuncs.com",
        "eci.oss.bucket=test-bucket",
        "ECI_OSS_BUCKET=test-bucket",
        "spring.datasource.url=jdbc:h2:mem:eci-runtime-cred-probe;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Import(RuntimeCredentialTestApiTest.RecordingProbeConfig.class)
class RuntimeCredentialTestApiTest {

    private static final String PATH = "/api/v1/eci/runtime-credentials";
    private static final String TEST_PATH = PATH + "/test";
    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingRuntimeCredentialProbe probe;

    @Autowired
    private OssProperties ossProperties;

    @BeforeEach
    void resetProbe() {
        probe.reset();
    }

    @Test
    void postRejectsMissingApiKey() throws Exception {
        mockMvc.perform(post(TEST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"OSS\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无效或缺失的 Api-Key"));
        assertThat(probe.ossCalls()).isZero();
        assertThat(probe.eciCalls()).isZero();
    }

    @Test
    void ossDraftReturns200ProbeGetsDraftGetHintUnchanged() throws Exception {
        assertOssSeedHintUnchanged();

        MvcResult result = mockMvc.perform(post(TEST_PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"OSS\",\"accessKeyId\":\"LTAIdraftoss99\",\"accessKeySecret\":\"draft-oss-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resource").value("OSS"))
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.accessKeyHint").value(CredentialHint.mask("LTAIdraftoss99")))
                .andExpect(jsonPath("$.data.detail").value(""))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("draft-oss-secret");
        assertThat(body).doesNotContain("seed-secret-value");
        assertThat(probe.ossCalls()).isEqualTo(1);
        assertThat(probe.lastOssAccessKeyId()).isEqualTo("LTAIdraftoss99");
        assertThat(probe.lastOssAccessKeySecret()).isEqualTo("draft-oss-secret");
        assertThat(probe.eciCalls()).isZero();
        assertOssSeedHintUnchanged();
    }

    @Test
    void omittedPairProbesSeedOssKeys() throws Exception {
        mockMvc.perform(post(TEST_PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"OSS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.accessKeyHint").value(CredentialHint.mask("LTAIseedoss01")));

        assertThat(probe.ossCalls()).isEqualTo(1);
        assertThat(probe.lastOssAccessKeyId()).isEqualTo("LTAIseedoss01");
        assertThat(probe.lastOssAccessKeySecret()).isEqualTo("seed-secret-value");
        assertThat(probe.eciCalls()).isZero();
        assertOssSeedHintUnchanged();
    }

    @Test
    void mockEciReturns400WithoutCallingProbeAndGetHintUnchanged() throws Exception {
        assertOssSeedHintUnchanged();

        mockMvc.perform(post(TEST_PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ECI_OPENAPI\",\"accessKeyId\":\"LTAIdraftkey01\",\"accessKeySecret\":\"draft-secret-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("mock")));

        assertThat(probe.eciCalls()).isZero();
        assertThat(probe.ossCalls()).isZero();
        assertOssSeedHintUnchanged();
    }

    private void assertOssSeedHintUnchanged() throws Exception {
        MvcResult result = mockMvc.perform(get(PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resources.OSS.accessKeyHint").value(CredentialHint.mask("LTAIseedoss01")))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("seed-secret-value");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("draft-oss-secret");
    }

    @Test
    void ossWithoutEndpointReturns400WithoutCallingProbe() throws Exception {
        String previous = ossProperties.getEndpoint();
        ossProperties.setEndpoint("");
        try {
            mockMvc.perform(post(TEST_PATH)
                            .header("X-Api-Key", API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"resource\":\"OSS\",\"accessKeyId\":\"LTAIdraftoss99\",\"accessKeySecret\":\"draft-oss-secret\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("endpoint")));
            assertThat(probe.ossCalls()).isZero();
        } finally {
            ossProperties.setEndpoint(previous);
        }
    }

    @Test
    void ossProbeFailureReturns200OkFalseWithoutSecret() throws Exception {
        probe.failOss(new IllegalStateException("OSS 鉴权失败（403）。secret=should-not-leak"));

        MvcResult result = mockMvc.perform(post(TEST_PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"OSS\",\"accessKeyId\":\"LTAIdraftoss99\",\"accessKeySecret\":\"draft-oss-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.resource").value("OSS"))
                .andExpect(jsonPath("$.data.detail").value(org.hamcrest.Matchers.containsString("403")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("draft-oss-secret");
        assertThat(body).doesNotContain("seed-secret-value");
        assertThat(probe.ossCalls()).isEqualTo(1);
        assertOssSeedHintUnchanged();
    }

    @TestConfiguration
    static class RecordingProbeConfig {
        @Bean
        @Primary
        RecordingRuntimeCredentialProbe recordingRuntimeCredentialProbe() {
            return new RecordingRuntimeCredentialProbe();
        }
    }
}
