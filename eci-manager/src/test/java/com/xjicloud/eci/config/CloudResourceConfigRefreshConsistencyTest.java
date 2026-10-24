package com.xjicloud.eci.config;

import com.xjicloud.eci.credential.AliyunResource;
import com.xjicloud.eci.credential.RuntimeCredentialService;
import com.xjicloud.eci.oss.OssStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 配置刷新与凭证热切的发布一致性：GET 快照、兼容属性、OSS 客户端和当前钥必须读到同一份状态，
 * 不出现旧钥配新配置的混合状态。只用现有公开入口（8081 配置 API 与凭证 API），不新增生产钩子。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "ECI_OSS_ENABLED=true",
        "ECI_OSS_ENDPOINT=https://oss-cn-hangzhou-internal.aliyuncs.com",
        "ECI_OSS_REGION=cn-hangzhou",
        "ECI_OSS_BUCKET=env-bucket",
        "eci.oss.access-key=LTAIseedoss01",
        "eci.oss.secret-key=seed-secret-value",
        "spring.datasource.url=jdbc:h2:mem:eci-config-refresh-consistency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CloudResourceConfigRefreshConsistencyTest {

    private static final String CONFIG_PATH = "/api/v1/eci/runtime-config";
    private static final String CREDENTIAL_PATH = "/api/v1/eci/runtime-credentials";
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

    @BeforeEach
    void resetToEnvironmentDefaultsAndSeedCredential() throws Exception {
        deleteSection("network");
        deleteSection("oss");
        deleteSection("advanced");
        putOssCredential("LTAIseedoss01", "seed-secret-value");
    }

    @Test
    void configRefreshRebuildsClientWithCurrentRuntimeCredential() throws Exception {
        putOssCredential("LTAIrefreshkey01", "refresh-secret-value");

        mockMvc.perform(put(CONFIG_PATH + "/oss")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ECI_OSS_BUCKET\":\"refresh-bucket\",\"ECI_OSS_PRESIGN_EXPIRATION_MINUTES\":\"45\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oss.fields[3].value").value("refresh-bucket"))
                .andExpect(jsonPath("$.data.oss.fields[3].source").value("database"))
                .andExpect(jsonPath("$.data.oss.fields[5].value").value("45"));

        // 同一份快照：GET、兼容属性、客户端目标与有效期一致
        assertThat(ossProperties.getBucket()).isEqualTo("refresh-bucket");
        assertThat(ossProperties.getPresignExpirationMinutes()).isEqualTo(45);
        mockMvc.perform(get(CONFIG_PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oss.fields[3].value").value("refresh-bucket"))
                .andExpect(jsonPath("$.data.oss.fields[5].value").value("45"));

        assertThat(runtimeCredentialService.currentPair(AliyunResource.OSS).accessKeyId())
                .isEqualTo("LTAIrefreshkey01");
        assertThat(ossStorageService.presignPutUrl("datasets/consistency/a.txt", "text/plain"))
                .contains("refresh-bucket")
                .contains("LTAIrefreshkey01")
                .contains("X-Amz-Expires=2700")
                .doesNotContain("LTAIseedoss01");
    }

    @Test
    void credentialRefreshKeepsCurrentConfigAndPublishesMatchingClient() throws Exception {
        mockMvc.perform(put(CONFIG_PATH + "/oss")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ECI_OSS_BUCKET\":\"config-bucket\",\"ECI_OSS_PRESIGN_EXPIRATION_MINUTES\":\"30\"}"))
                .andExpect(status().isOk());

        putOssCredential("LTAIhotswap0001", "hotswap-secret-value");

        assertThat(runtimeCredentialService.currentPair(AliyunResource.OSS).accessKeyId())
                .isEqualTo("LTAIhotswap0001");
        assertThat(ossProperties.getBucket()).isEqualTo("config-bucket");
        mockMvc.perform(get(CONFIG_PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oss.fields[3].value").value("config-bucket"))
                .andExpect(jsonPath("$.data.oss.fields[3].source").value("database"));

        // 凭证刷新后配置不被回退，客户端既不混用旧钥也不混用旧配置
        assertThat(ossStorageService.presignPutUrl("datasets/consistency/b.txt", "text/plain"))
                .contains("config-bucket")
                .contains("LTAIhotswap0001")
                .contains("X-Amz-Expires=1800")
                .doesNotContain("LTAIseedoss01");
    }

    private void putOssCredential(String accessKeyId, String accessKeySecret) throws Exception {
        mockMvc.perform(put(CREDENTIAL_PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"OSS\",\"accessKeyId\":\"" + accessKeyId
                                + "\",\"accessKeySecret\":\"" + accessKeySecret + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resources.OSS.accessKeyHint")
                        .value(com.xjicloud.eci.credential.CredentialHint.mask(accessKeyId)));
    }

    private void deleteSection(String section) throws Exception {
        mockMvc.perform(delete(CONFIG_PATH + "/" + section).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk());
    }
}
