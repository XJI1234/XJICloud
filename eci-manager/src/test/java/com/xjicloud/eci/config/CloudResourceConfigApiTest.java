package com.xjicloud.eci.config;

import com.xjicloud.eci.credential.AliyunResource;
import com.xjicloud.eci.credential.RuntimeCredentialService;
import com.xjicloud.eci.oss.OssStorageService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 8081 云资源配置 GET/PUT/DELETE 契约：分类返回、单类保存、恢复默认、字段错误、
 * Api-Key 鉴权，以及保存后兼容属性和 OSS 客户端刷新。独立内存库。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "eci.console.open-browser=false",
        "ECI_OSS_ENABLED=true",
        "ECI_OSS_ENDPOINT=https://oss-cn-hangzhou-internal.aliyuncs.com",
        "ECI_OSS_REGION=cn-hangzhou",
        "ECI_OSS_BUCKET=env-bucket",
        "ECI_CREATE_TIMEOUT_SEC=600",
        "eci.oss.access-key=LTAIseedoss01",
        "eci.oss.secret-key=seed-secret-value",
        "spring.datasource.url=jdbc:h2:mem:cloud-resource-config-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CloudResourceConfigApiTest {

    private static final String PATH = "/api/v1/eci/runtime-config";
    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EciProperties eciProperties;

    @Autowired
    private OssProperties ossProperties;

    @Autowired
    private OssStorageService ossStorageService;

    @Autowired
    private RuntimeCredentialService runtimeCredentialService;

    @Autowired
    private CloudResourceConfigRepository repository;

    private int startupCreateTimeoutSec;
    private String startupOssBucket;

    @BeforeAll
    void captureStartupProjection() {
        startupCreateTimeoutSec = eciProperties.getCreateTimeoutSec();
        startupOssBucket = ossProperties.getBucket();
    }

    @BeforeEach
    void resetCloudConfigOverrides() throws Exception {
        deleteSection("network");
        deleteSection("oss");
        deleteSection("advanced");
    }

    @Test
    void startupProjectsDatabaseAndEnvironmentSourcesIntoCompatibilityProperties() {
        assertThat(startupCreateTimeoutSec).isEqualTo(600);
        assertThat(startupOssBucket).isEqualTo("env-bucket");
        assertThat(eciProperties.getApiKey()).isEqualTo(API_KEY);
        assertThat(eciProperties.isMock()).isTrue();
    }

    @Test
    void getReturnsThreeSectionsWithValuesSourcesAndLabels() throws Exception {
        MvcResult result = mockMvc.perform(get(PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.network.id").value("network"))
                .andExpect(jsonPath("$.data.network.label").value("网络"))
                .andExpect(jsonPath("$.data.network.fields.length()").value(4))
                .andExpect(jsonPath("$.data.network.fields[0].key").value("ECI_REGION_ID"))
                .andExpect(jsonPath("$.data.network.fields[0].displayName").value("地域"))
                .andExpect(jsonPath("$.data.network.fields[0].value").value("cn-hangzhou"))
                .andExpect(jsonPath("$.data.network.fields[0].source").value("default"))
                .andExpect(jsonPath("$.data.oss.fields.length()").value(6))
                .andExpect(jsonPath("$.data.oss.fields[0].key").value("ECI_OSS_ENABLED"))
                .andExpect(jsonPath("$.data.oss.fields[0].value").value("true"))
                .andExpect(jsonPath("$.data.oss.fields[0].source").value("environment"))
                .andExpect(jsonPath("$.data.oss.fields[3].key").value("ECI_OSS_BUCKET"))
                .andExpect(jsonPath("$.data.oss.fields[3].value").value("env-bucket"))
                .andExpect(jsonPath("$.data.oss.fields[3].source").value("environment"))
                .andExpect(jsonPath("$.data.advanced.fields.length()").value(5))
                .andExpect(jsonPath("$.data.advanced.fields[0].displayName").value("默认镜像"))
                .andExpect(jsonPath("$.data.advanced.fields[0].groupLabel").value("镜像与共享内存"))
                .andExpect(jsonPath("$.data.advanced.fields[2].key").value("ECI_CREATE_TIMEOUT_SEC"))
                .andExpect(jsonPath("$.data.advanced.fields[2].value").value("600"))
                .andExpect(jsonPath("$.data.advanced.fields[2].source").value("environment"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("accessKey");
        assertThat(body).doesNotContain("secretKey");
    }

    @Test
    void putOnlyUpdatesRequestedSection() throws Exception {
        mockMvc.perform(put(PATH + "/network")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ECI_REGION_ID\":\"cn-shanghai\",\"ECI_VPC_ID\":\"vpc-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.network.fields[0].value").value("cn-shanghai"))
                .andExpect(jsonPath("$.data.network.fields[0].source").value("database"))
                .andExpect(jsonPath("$.data.oss.fields[3].value").value("env-bucket"))
                .andExpect(jsonPath("$.data.oss.fields[3].source").value("environment"))
                .andExpect(jsonPath("$.data.advanced.fields[2].value").value("600"));

        assertThat(eciProperties.getRegionId()).isEqualTo("cn-shanghai");
        assertThat(eciProperties.getVpcId()).isEqualTo("vpc-1");
        assertThat(ossProperties.getBucket()).isEqualTo("env-bucket");

        mockMvc.perform(put(PATH + "/advanced")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ECI_CREATE_TIMEOUT_SEC\":\"900\",\"ECI_FAILURE_LOG_GRACE_SEC\":\"0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.network.fields[0].value").value("cn-shanghai"))
                .andExpect(jsonPath("$.data.advanced.fields[2].value").value("900"))
                .andExpect(jsonPath("$.data.advanced.fields[3].value").value("1000"))
                .andExpect(jsonPath("$.data.advanced.fields[4].value").value("0"));

        assertThat(eciProperties.getCreateTimeoutSec()).isEqualTo(900);
        assertThat(eciProperties.getFailureLogGraceSec()).isZero();
    }

    @Test
    void putOssRefreshesClientAndKeepsCredentialOwnership() throws Exception {
        mockMvc.perform(put(PATH + "/oss")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ECI_OSS_BUCKET\":\"api-bucket\",\"ECI_OSS_PRESIGN_EXPIRATION_MINUTES\":\"30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oss.fields[3].value").value("api-bucket"))
                .andExpect(jsonPath("$.data.oss.fields[3].source").value("database"))
                .andExpect(jsonPath("$.data.oss.fields[5].value").value("30"));

        assertThat(ossProperties.getBucket()).isEqualTo("api-bucket");
        assertThat(ossProperties.getPresignExpirationMinutes()).isEqualTo(30);
        assertThat(ossProperties.getAccessKey()).isEqualTo("LTAIseedoss01");
        assertThat(runtimeCredentialService.currentPair(AliyunResource.OSS).accessKeyId())
                .isEqualTo("LTAIseedoss01");

        String url = ossStorageService.presignPutUrl("datasets/test/a.txt", "text/plain");
        assertThat(url).contains("api-bucket").contains("X-Amz-Expires=1800");
        assertThat(repository.findAll())
                .noneMatch(entity -> entity.getConfigKey().toLowerCase().contains("access"));
    }

    @Test
    void deleteRestoresEnvironmentOrDefaultValuesAndRefreshesOssClient() throws Exception {
        mockMvc.perform(put(PATH + "/network")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ECI_REGION_ID\":\"cn-shanghai\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(PATH + "/oss")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ECI_OSS_BUCKET\":\"put-bucket\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(PATH + "/advanced")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ECI_CREATE_TIMEOUT_SEC\":\"900\"}"))
                .andExpect(status().isOk());

        assertThat(ossStorageService.presignPutUrl("datasets/test/a.txt", "text/plain"))
                .contains("put-bucket");

        mockMvc.perform(delete(PATH + "/network").header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.network.fields[0].value").value("cn-hangzhou"))
                .andExpect(jsonPath("$.data.network.fields[0].source").value("default"));
        mockMvc.perform(delete(PATH + "/oss").header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oss.fields[3].value").value("env-bucket"))
                .andExpect(jsonPath("$.data.oss.fields[3].source").value("environment"));
        mockMvc.perform(delete(PATH + "/advanced").header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.advanced.fields[2].value").value("600"))
                .andExpect(jsonPath("$.data.advanced.fields[2].source").value("environment"));

        assertThat(eciProperties.getRegionId()).isEqualTo("cn-hangzhou");
        assertThat(eciProperties.getCreateTimeoutSec()).isEqualTo(600);
        assertThat(ossProperties.getBucket()).isEqualTo("env-bucket");
        assertThat(ossStorageService.presignPutUrl("datasets/test/a.txt", "text/plain"))
                .contains("env-bucket")
                .doesNotContain("put-bucket");
    }

    @Test
    void invalidValueReturnsFieldErrorAndKeepsCurrentRuntime() throws Exception {
        mockMvc.perform(put(PATH + "/oss")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ECI_OSS_ENABLED\":\"yes\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("启用 OSS")));

        assertThat(ossProperties.isEnabled()).isTrue();
        assertThat(ossStorageService.presignPutUrl("datasets/test/a.txt", "text/plain"))
                .contains("env-bucket");
        assertThat(repository.findById("ECI_OSS_ENABLED")).isEmpty();
    }

    @Test
    void missingApiKeyReturns401() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(put(PATH + "/network")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(PATH + "/network"))
                .andExpect(status().isUnauthorized());
    }

    private void deleteSection(String section) throws Exception {
        mockMvc.perform(delete(PATH + "/" + section).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk());
    }
}
