package com.xjicloud.eci.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API key filter must match the application path after a servlet context path is removed.
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=context-path-http-key",
        "server.servlet.context-path=/manager",
        "spring.datasource.url=jdbc:h2:mem:eci-api-key-filter-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=never",
        "eci.console.open-browser=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiKeyAuthFilterContextPathTest {

    private static final String CONTEXT_PATH = "/manager";
    private static final String PATH = "/api/v1/eci/meta";
    private static final String HEADER = "X-Api-Key";
    private static final String CURRENT_KEY = "context-path-http-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingApiKeyIsRejectedForRequestWithContextPath() throws Exception {
        mockMvc.perform(apiRequest())
                .andExpect(result -> assertRequestUriIncludesContextPath(result))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void wrongApiKeyIsRejectedForRequestWithContextPath() throws Exception {
        mockMvc.perform(apiRequest().header(HEADER, "wrong-context-path-http-key"))
                .andExpect(result -> assertRequestUriIncludesContextPath(result))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void configuredApiKeyContinuesFilterChainForRequestWithContextPath() throws Exception {
        mockMvc.perform(apiRequest().header(HEADER, CURRENT_KEY))
                .andExpect(result -> assertRequestUriIncludesContextPath(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private MockHttpServletRequestBuilder apiRequest() {
        return get(CONTEXT_PATH + PATH).contextPath(CONTEXT_PATH);
    }

    private static void assertRequestUriIncludesContextPath(org.springframework.test.web.servlet.MvcResult result) {
        assertThat(result.getRequest().getRequestURI()).isEqualTo(CONTEXT_PATH + PATH);
        assertThat(result.getRequest().getContextPath()).isEqualTo(CONTEXT_PATH);
    }
}
