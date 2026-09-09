package com.xjicloud.eci.credential;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.xjicloud.eci.config.EciProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 当前钥 GET/PUT 契约：鉴权、OSS 种子、成对 PUT、永不返回 SK。独立内存库。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.oss.access-key=LTAIseedoss01",
        "eci.oss.secret-key=seed-secret-value",
        "spring.datasource.url=jdbc:h2:mem:eci-runtime-cred;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class RuntimeCredentialApiTest {

    private static final String PATH = "/api/v1/eci/runtime-credentials";
    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EciProperties eciProperties;

    @Test
    void getRejectsMissingApiKey() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAfterOssSeedShowsConfiguredWithoutSecret() throws Exception {
        MvcResult result = mockMvc.perform(get(PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mock").value(true))
                .andExpect(jsonPath("$.data.resources.OSS.configured").value(true))
                .andExpect(jsonPath("$.data.resources.OSS.accessKeyHint").value("LTAI****ss01"))
                .andExpect(jsonPath("$.data.resources.OSS.mode").value("STATIC_AK"))
                .andExpect(jsonPath("$.data.resources.OSS.accessKeySecret").doesNotExist())
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.mode").value("MOCK"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("seed-secret-value");
        assertThat(body).doesNotContain("accessKeySecret");
    }

    @Test
    void putEciPairUpdatesHintAndGetMatches() throws Exception {
        mockMvc.perform(put(PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ECI_OPENAPI\",\"accessKeyId\":\"LTAIputkey0001\",\"accessKeySecret\":\"put-secret-value\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.configured").value(true))
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.accessKeyHint").value("LTAI****0001"))
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.mode").value("MOCK"));

        mockMvc.perform(get(PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.accessKeyHint").value("LTAI****0001"))
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.configured").value(true));
    }

    @Test
    void putIdWithoutSecretReturns400() throws Exception {
        mockMvc.perform(put(PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ECI_OPENAPI\",\"accessKeyId\":\"LTAIputkey0001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void putUnknownResourceReturns400() throws Exception {
        mockMvc.perform(put(PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"NOT_A_RESOURCE\",\"accessKeyId\":\"LTAIputkey0001\",\"accessKeySecret\":\"put-secret-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void putEciInMockDoesNotThrow() throws Exception {
        mockMvc.perform(put(PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ECI_OPENAPI\",\"accessKeyId\":\"LTAImockkey0001\",\"accessKeySecret\":\"mock-secret-value\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mock").value(true))
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.accessKeyHint").value("LTAI****0001"));
    }

    @Test
    void putEciDoesNotMutateSeedProperties() throws Exception {
        String seedId = eciProperties.getAccessKeyId();

        mockMvc.perform(put(PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ECI_OPENAPI\",\"accessKeyId\":\"LTAIputkey0001\",\"accessKeySecret\":\"put-secret-value\"}"))
                .andExpect(status().isOk());

        assertThat(eciProperties.getAccessKeyId()).isEqualTo(seedId);
        assertThat(eciProperties.getAccessKeyId()).isNotEqualTo("LTAIputkey0001");
    }

    @Test
    void concurrentGetAndPutDoNotReturnServerError() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                if (idx % 2 == 0) {
                    return mockMvc.perform(get(PATH).header("X-Api-Key", API_KEY))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }
                String accessKeyId = "LTAIconcur" + String.format("%04d", idx);
                return mockMvc.perform(put(PATH)
                                .header("X-Api-Key", API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"resource\":\"ECI_OPENAPI\",\"accessKeyId\":\""
                                        + accessKeyId + "\",\"accessKeySecret\":\"concurrent-secret\"}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> future : futures) {
            assertThat(future.get(15, TimeUnit.SECONDS)).isEqualTo(200);
        }
        pool.shutdownNow();

        mockMvc.perform(put(PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ECI_OPENAPI\",\"accessKeyId\":\"LTAIfinalkey0001\",\"accessKeySecret\":\"final-secret-value\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get(PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.configured").value(true))
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.accessKeyHint").value("LTAI****0001"));
    }
}
