package com.xjicloud.eci.credential;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * overlap=0：PUT 后旧钥立刻 401。独立内存库。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.api-key-overlap-sec=0",
        "spring.datasource.url=jdbc:h2:mem:eci-http-api-key-overlap0;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class HttpApiKeyOverlapZeroApiTest {

    private static final String PUT_PATH = "/api/v1/eci/credentials/http-api-key";
    private static final String TASKS_PATH = "/api/v1/eci/tasks";
    private static final String SEED = "test-api-key";
    private static final String NEXT = "next-http-api-key-01";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void putWithOverlapZeroRejectsOldKeyImmediately() throws Exception {
        mockMvc.perform(put(PUT_PATH)
                        .header("X-Api-Key", SEED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + NEXT + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousActive").value(false))
                .andExpect(jsonPath("$.data.previousExpiresAt").doesNotExist())
                .andExpect(jsonPath("$.data.apiKeyHint").value("next****y-01"));

        mockMvc.perform(get(TASKS_PATH).header("X-Api-Key", NEXT))
                .andExpect(status().isOk());
        mockMvc.perform(get(TASKS_PATH).header("X-Api-Key", SEED))
                .andExpect(status().isUnauthorized());
    }
}
