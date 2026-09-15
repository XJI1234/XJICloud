package com.xjicloud.eci.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.credential.HttpApiKeyStore;
import com.xjicloud.eci.credential.MapRuntimeConfigStore;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filter 两种 401：未配置当前钥 vs 无效/缺失 Api-Key。不 mock SUT。
 */
class ApiKeyAuthFilterTest {

    private static final String SEED = "seed-http-api-key";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MapRuntimeConfigStore config;
    private EciProperties properties;
    private HttpApiKeyStore store;
    private ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        config = new MapRuntimeConfigStore();
        properties = new EciProperties();
        properties.setApiKey(SEED);
        properties.setApiKeyOverlapSec(3600);
        store = new HttpApiKeyStore(config, properties, Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC));
        store.afterSingletonsInstantiated();
        filter = new ApiKeyAuthFilter(store, MAPPER);
    }

    @Test
    void unconfiguredCurrentKeyReturns401ConfiguredMessageAndDoesNotContinue() throws Exception {
        properties.setApiKey("");
        config.delete("eci.api-key");
        store = new HttpApiKeyStore(config, properties, Clock.systemUTC());
        store.afterSingletonsInstantiated();
        filter = new ApiKeyAuthFilter(store, MAPPER);

        MockHttpServletResponse response = perform("GET", "/api/v1/eci/tasks", null);

        assertUnauthorized(response, "服务器未配置 eci.api-key");
    }

    @Test
    void missingHeaderReturns401InvalidMessage() throws Exception {
        MockHttpServletResponse response = perform("GET", "/api/v1/eci/tasks", null);

        assertUnauthorized(response, "无效或缺失的 Api-Key");
    }

    @Test
    void wrongKeyReturns401InvalidMessage() throws Exception {
        MockHttpServletResponse response = perform("GET", "/api/v1/eci/tasks", "wrong-http-api-key");

        assertUnauthorized(response, "无效或缺失的 Api-Key");
    }

    @Test
    void matchingKeyContinuesFilterChain() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/eci/tasks", SEED);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private MockHttpServletResponse perform(String method, String uri, String apiKey)
            throws ServletException, IOException {
        MockHttpServletRequest request = request(method, uri, apiKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isNull();
        return response;
    }

    private static MockHttpServletRequest request(String method, String uri, String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        if (apiKey != null) {
            request.addHeader(ApiKeyAuthFilter.HEADER_API_KEY, apiKey);
        }
        return request;
    }

    private static void assertUnauthorized(MockHttpServletResponse response, String message) throws Exception {
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        JsonNode body = MAPPER.readTree(response.getContentAsByteArray());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("message").asText()).isEqualTo(message);
        assertThat(body.path("data").isNull()).isTrue();
    }
}
