package com.xjicloud.eci.credential;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP Api-Key 双钥契约。独立内存库；每方法重建上下文以免钥互相污染。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.api-key-overlap-sec=3600",
        "spring.datasource.url=jdbc:h2:mem:eci-http-api-key;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HttpApiKeyApiTest {

    private static final String PUT_PATH = "/api/v1/eci/credentials/http-api-key";
    private static final String REVOKE_PATH = "/api/v1/eci/credentials/revoke-previous-api-key";
    private static final String TASKS_PATH = "/api/v1/eci/tasks";
    private static final String SEED = "test-api-key";
    private static final String NEXT = "next-http-api-key-01";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void putRejectsMissingApiKey() throws Exception {
        mockMvc.perform(put(PUT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + NEXT + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无效或缺失的 Api-Key"));
    }

    @Test
    void putWithoutBodyReturns400() throws Exception {
        mockMvc.perform(put(PUT_PATH)
                        .header("X-Api-Key", SEED)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("apiKey")));
    }

    @Test
    void putEmptyObjectReturns400() throws Exception {
        mockMvc.perform(put(PUT_PATH)
                        .header("X-Api-Key", SEED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("apiKey")));
    }

    @Test
    void rotateBlankApiKeyReturns400() throws Exception {
        mockMvc.perform(put(PUT_PATH)
                        .header("X-Api-Key", SEED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("apiKey")));
    }

    @Test
    void rotateAcceptsNewAndOldDuringOverlap() throws Exception {
        mockMvc.perform(put(PUT_PATH)
                        .header("X-Api-Key", SEED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + NEXT + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.apiKeyHint").value("next****y-01"))
                .andExpect(jsonPath("$.data.previousActive").value(true))
                .andExpect(jsonPath("$.data.previousExpiresAt").isNotEmpty());

        mockMvc.perform(get(TASKS_PATH).header("X-Api-Key", NEXT))
                .andExpect(status().isOk());
        mockMvc.perform(get(TASKS_PATH).header("X-Api-Key", SEED))
                .andExpect(status().isOk());
    }

    @Test
    void rotateResponseOmitsPlaintextApiKey() throws Exception {
        MvcResult putResult = mockMvc.perform(put(PUT_PATH)
                        .header("X-Api-Key", SEED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + NEXT + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = putResult.getResponse().getContentAsString();
        assertThat(body).doesNotContain(NEXT);
        assertThat(body).doesNotContain("\"apiKey\":");
        assertThat(body).contains("\"apiKeyHint\"");
    }

    @Test
    void revokeCutsPreviousKey() throws Exception {
        mockMvc.perform(put(PUT_PATH)
                        .header("X-Api-Key", SEED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + NEXT + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(REVOKE_PATH).header("X-Api-Key", NEXT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.previousActive").value(false))
                .andExpect(jsonPath("$.data.previousExpiresAt").doesNotExist())
                .andExpect(jsonPath("$.data.apiKeyHint").value("next****y-01"));

        mockMvc.perform(get(TASKS_PATH).header("X-Api-Key", NEXT))
                .andExpect(status().isOk());
        mockMvc.perform(get(TASKS_PATH).header("X-Api-Key", SEED))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("无效或缺失的 Api-Key"));
    }

    @Test
    void sameKeyPutIsNoOpAndSeedStillWorks() throws Exception {
        mockMvc.perform(put(PUT_PATH)
                        .header("X-Api-Key", SEED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + SEED + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKeyHint").value("test****-key"))
                .andExpect(jsonPath("$.data.previousActive").value(false));

        mockMvc.perform(get(TASKS_PATH).header("X-Api-Key", SEED))
                .andExpect(status().isOk());
    }

    @Test
    void revokeWithoutPriorRotateKeepsSeed() throws Exception {
        mockMvc.perform(post(REVOKE_PATH).header("X-Api-Key", SEED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousActive").value(false))
                .andExpect(jsonPath("$.data.apiKeyHint").value("test****-key"));

        mockMvc.perform(get(TASKS_PATH).header("X-Api-Key", SEED))
                .andExpect(status().isOk());
    }
}
