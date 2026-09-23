package com.xjicloud.eci.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    void cancellingTaskBehaviorKeepsOnlyRealTerminalsPolling() throws Exception {
        String script = mockMvc.perform(get("/console/console.js"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        Matcher terminalMatcher = Pattern.compile("var TERMINAL = \\{([^}]*)\\};").matcher(script);
        assertThat(terminalMatcher.find())
                .as("Detects a production mutation that changes which states stop polling")
                .isTrue();
        assertThat(objectKeys(terminalMatcher.group(1)))
                .as("Detects a production mutation that makes CANCELLING or another non-terminal state terminal")
                .containsExactly("SUCCEEDED", "FAILED", "CANCELLED");

        Matcher labelsMatcher = Pattern.compile("var STATUS_LABELS = \\{([^}]*)\\};").matcher(script);
        assertThat(labelsMatcher.find())
                .as("Detects a missing CANCELLING status label map")
                .isTrue();
        assertThat(labelsMatcher.group(1).trim())
                .as("Detects a wrong CANCELLING status label")
                .isEqualTo("CANCELLING: \"正在清理\"");

        String statusLabelHelper = script.substring(
                script.indexOf("function statusLabel(status)"),
                script.indexOf("function getKey()"));
        assertThat(statusLabelHelper)
                .as("Detects a production mutation that ignores STATUS_LABELS when resolving a task status")
                .contains("return STATUS_LABELS[status] || status || \"?\";");

        String renderTaskList = script.substring(
                script.indexOf("function renderTaskList(tasks)"),
                script.indexOf("function renderTaskDetail(task)"));
        assertThat(renderTaskList)
                .as("Detects a production mutation that bypasses the CANCELLING label in the task list")
                .contains("pill.textContent = statusLabel(task.status);");

        String renderTaskDetail = script.substring(
                script.indexOf("function renderTaskDetail(task)"),
                script.indexOf("function loadLogs(task)"));
        assertThat(renderTaskDetail)
                .as("Detects a production mutation that bypasses the CANCELLING label in task detail")
                .contains("setText($(\"task-status-line\"), \"status=\" + statusLabel(task.status)");
        assertThat(renderTaskDetail)
                .as("Detects a production mutation that leaves cancel enabled while CANCELLING")
                .contains("$(\"cancel-task\").disabled = !!TERMINAL[task.status] || task.status === \"CANCELLING\";");

        String pollLoop = script.substring(
                script.indexOf("function pollLoop()"),
                script.indexOf("function startPolling()"));
        assertThat(pollLoop)
                .as("Detects a production mutation that stops polling on CANCELLING")
                .contains("if (!selectedTask || TERMINAL[selectedTask.status]) return;")
                .doesNotContain("CANCELLING");
        assertThat(pollLoop)
                .as("Detects a production mutation that stops requesting the selected task detail")
                .contains("api(\"/tasks/\" + selectedTask.id)");
    }

    private static List<String> objectKeys(String objectBody) {
        Matcher matcher = Pattern.compile("([A-Z]+)\\s*:").matcher(objectBody);
        List<String> keys = new ArrayList<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
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
