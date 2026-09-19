package com.xjicloud.eci.auth;

import com.xjicloud.eci.config.EciProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP Api-Key 的外部行为：配置钥是唯一权威，不能被落盘旧钥取代。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=configured-http-key",
        "spring.datasource.url=jdbc:h2:mem:eci-api-key-filter;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.sql.init.data-locations=classpath:api-key-filter-legacy.sql"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiKeyAuthFilterTest {

    private static final String PATH = "/api/v1/eci/meta";
    private static final String HEADER = "X-Api-Key";
    private static final String CURRENT_KEY = "configured-http-key";
    private static final String LEGACY_KEY = "legacy-http-key";
    private static final String LEGACY_PREVIOUS_KEY = "legacy-previous-http-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingHeaderReturns401AndDoesNotReachEndpoint() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无效或缺失的 Api-Key"));
    }

    @Test
    void wrongKeyReturns401AndDoesNotReachEndpoint() throws Exception {
        mockMvc.perform(get(PATH).header(HEADER, "wrong-http-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无效或缺失的 Api-Key"));
    }

    @Test
    void exactConfiguredKeyContinuesFilterChain() throws Exception {
        mockMvc.perform(get(PATH).header(HEADER, CURRENT_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void requestHeaderWhitespaceIsNotTrimmed() throws Exception {
        mockMvc.perform(get(PATH).header(HEADER, " " + CURRENT_KEY + " "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无效或缺失的 Api-Key"));
    }

    @Test
    void persistedLegacyKeyIsNotAccepted() throws Exception {
        mockMvc.perform(get(PATH).header(HEADER, LEGACY_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无效或缺失的 Api-Key"));
    }

    @Test
    void persistedLegacyPreviousKeyIsNotAccepted() throws Exception {
        mockMvc.perform(get(PATH).header(HEADER, LEGACY_PREVIOUS_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无效或缺失的 Api-Key"));
    }
}

/**
 * 空白配置仍必须阻断请求，即使客户端提供了任意请求头。
 * 仅替换启动校验 runner，让应用能启动到实际 HTTP 过滤器；不替换被测组件。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "spring.datasource.url=jdbc:h2:mem:eci-api-key-filter-empty;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.sql.init.data-locations=classpath:api-key-filter-legacy.sql"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiKeyAuthFilterEmptyConfigurationTest {

    @DynamicPropertySource
    static void registerWhitespaceApiKey(DynamicPropertyRegistry registry) {
        registry.add("eci.api-key", () -> "   ");
    }

    @MockBean(name = "apiKeyStartupCheck")
    private ApplicationRunner startupCheck;

    @Autowired
    private EciProperties eciProperties;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blankConfiguredKeyReturns401AndDoesNotReachEndpoint() throws Exception {
        assertThat(eciProperties.getApiKey())
                .isNotEmpty()
                .isEqualTo("   ");

        mockMvc.perform(get("/api/v1/eci/meta").header("X-Api-Key", "legacy-http-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("服务器未配置 eci.api-key"));
    }
}
