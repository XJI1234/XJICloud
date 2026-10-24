package com.xjicloud.eci.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 无数据库/环境覆盖时，集中定义的默认值必须真实投影到兼容属性，并与 GET 的最终值一致；
 * application.yml 的历史默认（nginx:alpine / false / 300 / 60）不得覆盖集中默认。
 * 用例不做任何 ECI_* 覆盖；本机导出受管 ECI_* 环境变量时前提不成立，会显式失败。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "spring.datasource.url=jdbc:h2:mem:eci-config-default-projection;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CloudResourceConfigDefaultProjectionTest {

    private static final String PATH = "/api/v1/eci/runtime-config";
    private static final String API_KEY = "test-api-key";

    /** 本用例不设任何 ECI_* 测试属性，因此全部受管键都必须来自集中默认。 */
    private static final List<String> MANAGED_KEYS = List.of(
            "ECI_REGION_ID",
            "ECI_VPC_ID",
            "ECI_VSWITCH_ID",
            "ECI_SECURITY_GROUP_ID",
            "ECI_OSS_ENABLED",
            "ECI_OSS_ENDPOINT",
            "ECI_OSS_REGION",
            "ECI_OSS_BUCKET",
            "ECI_OSS_PATH_STYLE_ACCESS",
            "ECI_OSS_PRESIGN_EXPIRATION_MINUTES",
            "ECI_DEFAULT_IMAGE",
            "ECI_DEFAULT_SHM_SIZE",
            "ECI_CREATE_TIMEOUT_SEC",
            "ECI_FAILURE_LOG_TAIL_LINES",
            "ECI_FAILURE_LOG_GRACE_SEC");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EciProperties eciProperties;

    @Autowired
    private OssProperties ossProperties;

    @Autowired
    private Environment environment;

    @Test
    void centralizedDefaultsAreProjectedIntoCompatibilityProperties() {
        assertThat(overriddenManagedKeys())
                .as("本机存在受管 ECI_* 环境变量，无法验证“无覆盖默认投影”")
                .isEmpty();

        assertThat(eciProperties.getDefaultImage()).isEmpty();
        assertThat(ossProperties.isEnabled()).isTrue();
        assertThat(eciProperties.getCreateTimeoutSec()).isEqualTo(1800);
        assertThat(ossProperties.getPresignExpirationMinutes()).isEqualTo(1440);

        assertThat(eciProperties.getRegionId()).isEqualTo("cn-hangzhou");
        assertThat(ossProperties.getEndpoint()).isEqualTo("https://oss-cn-hangzhou-internal.aliyuncs.com");
        assertThat(ossProperties.getRegion()).isEqualTo("cn-hangzhou");
        assertThat(ossProperties.getBucket()).isEmpty();
        assertThat(ossProperties.isPathStyleAccess()).isFalse();
        assertThat(eciProperties.getDefaultShmSize()).isEmpty();
        assertThat(eciProperties.getFailureLogTailLines()).isEqualTo(1000);
        assertThat(eciProperties.getFailureLogGraceSec()).isEqualTo(90);
    }

    @Test
    void getReportsTheSameValuesAsTheProjectedProperties() throws Exception {
        MvcResult result = mockMvc.perform(get(PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.network.fields[0].value").value("cn-hangzhou"))
                .andExpect(jsonPath("$.data.network.fields[0].source").value("default"))
                .andExpect(jsonPath("$.data.oss.fields[0].value").value("true"))
                .andExpect(jsonPath("$.data.oss.fields[0].source").value("default"))
                .andExpect(jsonPath("$.data.oss.fields[5].value").value("1440"))
                .andExpect(jsonPath("$.data.oss.fields[5].source").value("default"))
                .andExpect(jsonPath("$.data.advanced.fields[0].value").value(""))
                .andExpect(jsonPath("$.data.advanced.fields[0].source").value("default"))
                .andExpect(jsonPath("$.data.advanced.fields[2].value").value("1800"))
                .andExpect(jsonPath("$.data.advanced.fields[2].source").value("default"))
                .andReturn();

        assertThat(valuesByKey(result))
                .containsEntry("ECI_REGION_ID", eciProperties.getRegionId())
                .containsEntry("ECI_VPC_ID", eciProperties.getVpcId())
                .containsEntry("ECI_VSWITCH_ID", eciProperties.getVswitchId())
                .containsEntry("ECI_SECURITY_GROUP_ID", eciProperties.getSecurityGroupId())
                .containsEntry("ECI_OSS_ENABLED", String.valueOf(ossProperties.isEnabled()))
                .containsEntry("ECI_OSS_ENDPOINT", ossProperties.getEndpoint())
                .containsEntry("ECI_OSS_REGION", ossProperties.getRegion())
                .containsEntry("ECI_OSS_BUCKET", ossProperties.getBucket())
                .containsEntry("ECI_OSS_PATH_STYLE_ACCESS", String.valueOf(ossProperties.isPathStyleAccess()))
                .containsEntry("ECI_OSS_PRESIGN_EXPIRATION_MINUTES",
                        String.valueOf(ossProperties.getPresignExpirationMinutes()))
                .containsEntry("ECI_DEFAULT_IMAGE", eciProperties.getDefaultImage())
                .containsEntry("ECI_DEFAULT_SHM_SIZE", eciProperties.getDefaultShmSize())
                .containsEntry("ECI_CREATE_TIMEOUT_SEC", String.valueOf(eciProperties.getCreateTimeoutSec()))
                .containsEntry("ECI_FAILURE_LOG_TAIL_LINES", String.valueOf(eciProperties.getFailureLogTailLines()))
                .containsEntry("ECI_FAILURE_LOG_GRACE_SEC", String.valueOf(eciProperties.getFailureLogGraceSec()));
    }

    private List<String> overriddenManagedKeys() {
        return MANAGED_KEYS.stream().filter(environment::containsProperty).toList();
    }

    private Map<String, String> valuesByKey(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        Map<String, String> values = new LinkedHashMap<>();
        for (String section : List.of("network", "oss", "advanced")) {
            for (JsonNode field : data.path(section).path("fields")) {
                values.put(field.path("key").asText(), field.path("value").asText());
            }
        }
        return values;
    }
}
