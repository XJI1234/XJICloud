package com.xjicloud.eci.config;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 操作台静态资源对浏览器公开；API 仍走 Api-Key。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.console.open-browser=false",
        "spring.datasource.url=jdbc:h2:mem:eci-console-static;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ConsoleStaticResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootServesConsoleHtmlWithoutApiKey() throws Exception {
        MvcResult root = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn();
        String forwarded = root.getResponse().getForwardedUrl();
        if (forwarded != null) {
            assertThat(forwarded).isEqualTo("index.html");
            mockMvc.perform(get("/" + forwarded))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("id=\"eci-console\"")))
                    .andExpect(content().string(containsString("sessionStorage")));
        } else {
            assertThat(root.getResponse().getContentAsString()).contains("id=\"eci-console\"");
        }
    }

    @Test
    void indexHtmlIsPublic() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"eci-console\"")));
    }

    @Test
    void consoleAssetsArePublic() throws Exception {
        mockMvc.perform(get("/console/console.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(containsString("--bg")));

        mockMvc.perform(get("/console/console.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("X-Api-Key")))
                .andExpect(content().string(containsString("sessionStorage")));
    }

    @Test
    void consoleHtmlKeepsGateAndCloudCredentialTabWithoutHttpKeyRotationUi() throws Exception {
        String html = mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html)
                .contains("id=\"key-gate\"")
                .contains("id=\"key-form\"")
                .contains("id=\"change-key\" type=\"button\" class=\"ghost\">更换 Api-Key</button>")
                .contains("data-tab=\"creds\"")
                .contains("id=\"tab-creds\"")
                .contains("当前云钥")
                .doesNotContain("tab-httpkey")
                .doesNotContain("revoke-prev")
                .doesNotContain("/credentials/http-api-key")
                .doesNotContain("/credentials/revoke-previous-api-key");
    }

    @Test
    void consoleScriptKeepsSessionStorageApiKeyGateAndTopBarRePromptWithoutHttpKeyCalls() throws Exception {
        String script = mockMvc.perform(get("/console/console.js"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(script)
                .contains("sessionStorage.getItem(STORAGE_KEY)")
                .contains("sessionStorage.setItem(STORAGE_KEY, value)")
                .contains("\"X-Api-Key\": getKey()")
                .contains("$(\"change-key\").addEventListener(\"click\", function () {\n    showGate(\"\");\n  });")
                .doesNotContain("tab-httpkey")
                .doesNotContain("revoke-prev")
                .doesNotContain("/credentials/http-api-key")
                .doesNotContain("/credentials/revoke-previous-api-key");
    }

    @Test
    void faviconIsPublic() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownPathRemainsDenied() throws Exception {
        mockMvc.perform(get("/not-a-console-page"))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiStillRequiresApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/eci/tasks"))
                .andExpect(status().isUnauthorized());
    }
}
