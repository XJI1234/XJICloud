package com.xjicloud.eci.credential;

import com.aliyuncs.auth.DefaultCredentialsProvider;
import com.aliyuncs.exceptions.ClientException;
import com.xjicloud.eci.oss.OssStorageService;
import com.xjicloud.eci.provider.AliyunEciProvider;
import com.xjicloud.eci.provider.CloudOperationException;
import com.xjicloud.eci.provider.EciProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真云 provider 的空初始钥路径。只 mock SDK 凭证边界，不创建 ECI 资源或请求 OSS。
 */
@SpringBootTest(properties = {
        "eci.mock=false",
        "eci.api-key=runtime-credential-test-key",
        "eci.region-id=cn-hangzhou",
        "eci.vswitch-id=vsw-test",
        "eci.security-group-id=sg-test",
        "eci.access-key-id=",
        "eci.access-key-secret=",
        "eci.ecs-ram-role-name=",
        "eci.console.open-browser=false",
        "eci.oss.enabled=true",
        "eci.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
        "eci.oss.region=cn-hangzhou",
        "eci.oss.bucket=eci-runtime-test",
        "eci.oss.access-key=",
        "eci.oss.secret-key=",
        "spring.datasource.url=jdbc:h2:mem:eci-runtime-cloud-startup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class RuntimeCredentialCloudStartupTest {

    private static final String API_KEY = "runtime-credential-test-key";
    private static final String PATH = "/api/v1/eci/runtime-credentials";

    private static final MockedConstruction<DefaultCredentialsProvider> DEFAULT_CHAIN =
            mockConstruction(DefaultCredentialsProvider.class, (provider, context) -> {
                try {
                    doAnswer(invocation -> {
                        throw new ClientException("No default credentials configured by test");
                    }).when(provider).getCredentials();
                } catch (ClientException exception) {
                    throw new IllegalStateException(exception);
                }
            });

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EciProvider eciProvider;

    @Autowired
    private OssStorageService ossStorageService;

    @AfterAll
    static void restoreSdkDefaultChain() {
        DEFAULT_CHAIN.close();
    }

    @Test
    void emptyCloudKeysKeepManagementAvailableAndCanBeSetPerResource() throws Exception {
        assertThat(eciProvider).isInstanceOf(AliyunEciProvider.class);

        mockMvc.perform(get(PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mock").value(false))
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.configured").value(false))
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.mode").value("DEFAULT_CHAIN"))
                .andExpect(jsonPath("$.data.resources.OSS.configured").value(false));

        assertThat(DEFAULT_CHAIN.constructed()).hasSize(1);
        DefaultCredentialsProvider defaultProvider = DEFAULT_CHAIN.constructed().get(0);
        verify(defaultProvider, never()).getCredentials();
        assertThatThrownBy(() -> eciProvider.describe("test-instance"))
                .isInstanceOf(CloudOperationException.class)
                .hasMessageContaining("credential");
        verify(defaultProvider).getCredentials();

        mockMvc.perform(put(PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"ECI_OPENAPI\",\"accessKeyId\":\"LTAItestkey0001\",\"accessKeySecret\":\"eci-test-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.configured").value(true))
                .andExpect(jsonPath("$.data.resources.ECI_OPENAPI.mode").value("STATIC_AK"));

        mockMvc.perform(put(PATH)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"OSS\",\"accessKeyId\":\"LTAItestoss0001\",\"accessKeySecret\":\"oss-test-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resources.OSS.configured").value(true))
                .andExpect(jsonPath("$.data.resources.OSS.mode").value("STATIC_AK"));

        assertThat(ossStorageService.presignPutUrl("datasets/test/image.jpg", "image/jpeg"))
                .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                .contains("datasets/test/image.jpg");
    }
}
