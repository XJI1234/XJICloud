package com.xjicloud.admin;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xjicloud.admin.EciManagerCloudResourceConfigDtos.CloudResourceConfigField;
import com.xjicloud.admin.EciManagerCloudResourceConfigDtos.CloudResourceConfigSection;
import com.xjicloud.admin.EciManagerCloudResourceConfigDtos.CloudResourceConfigView;
import com.xjicloud.common.BusinessException;
import com.xjicloud.common.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 8080 云资源配置管理接口契约：管理员鉴权、GET 分组响应、分类 PUT/DELETE 转发文本值、字段错误透传。
 */
@WebMvcTest(CloudResourceConfigController.class)
@Import(CloudAccessKeyControllerAuthTest.TestSecurityConfig.class)
@ContextConfiguration(classes = {
        CloudResourceConfigController.class,
        CloudAccessKeyControllerAuthTest.TestSecurityConfig.class,
        GlobalExceptionHandler.class
})
class CloudResourceConfigControllerTest {

    private static final String PATH = "/api/v1/admin/cloud-resource-config";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EciManagerClient eciManagerClient;

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminReturns403() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(PATH + "/network")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(PATH + "/network"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminGetReturnsGroupedSections() throws Exception {
        when(eciManagerClient.getCloudResourceConfig()).thenReturn(view("vpc-test-1"));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.network.id").value("network"))
                .andExpect(jsonPath("$.data.network.label").value("网络"))
                .andExpect(jsonPath("$.data.network.fields[0].key").value("ECI_VPC_ID"))
                .andExpect(jsonPath("$.data.network.fields[0].value").value("vpc-test-1"))
                .andExpect(jsonPath("$.data.network.fields[0].source").value("database"))
                .andExpect(jsonPath("$.data.oss.id").value("oss"))
                .andExpect(jsonPath("$.data.advanced.id").value("advanced"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putNetworkForwardsTextValues() throws Exception {
        when(eciManagerClient.saveCloudResourceConfigSection("network", Map.of("ECI_VPC_ID", "vpc-test-2")))
                .thenReturn(view("vpc-test-2"));

        mockMvc.perform(put(PATH + "/network")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ECI_VPC_ID":"vpc-test-2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.network.fields[0].value").value("vpc-test-2"));

        verify(eciManagerClient).saveCloudResourceConfigSection("network", Map.of("ECI_VPC_ID", "vpc-test-2"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putOssAndAdvancedKeepBooleanTextValues() throws Exception {
        when(eciManagerClient.saveCloudResourceConfigSection("oss", Map.of("ECI_OSS_ENABLED", "true")))
                .thenReturn(view("vpc-test-1"));
        when(eciManagerClient.saveCloudResourceConfigSection("advanced", Map.of("ECI_CREATE_TIMEOUT_SEC", "900")))
                .thenReturn(view("vpc-test-1"));

        mockMvc.perform(put(PATH + "/oss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ECI_OSS_ENABLED":"true"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put(PATH + "/advanced")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ECI_CREATE_TIMEOUT_SEC":"900"}
                                """))
                .andExpect(status().isOk());

        verify(eciManagerClient).saveCloudResourceConfigSection("oss", Map.of("ECI_OSS_ENABLED", "true"));
        verify(eciManagerClient).saveCloudResourceConfigSection("advanced", Map.of("ECI_CREATE_TIMEOUT_SEC", "900"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteResetsRequestedSection() throws Exception {
        when(eciManagerClient.resetCloudResourceConfigSection("network")).thenReturn(view("vpc-test-1"));
        when(eciManagerClient.resetCloudResourceConfigSection("oss")).thenReturn(view("vpc-test-1"));
        when(eciManagerClient.resetCloudResourceConfigSection("advanced")).thenReturn(view("vpc-test-1"));

        mockMvc.perform(delete(PATH + "/network"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.network.id").value("network"));
        mockMvc.perform(delete(PATH + "/oss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oss.id").value("oss"));
        mockMvc.perform(delete(PATH + "/advanced"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.advanced.id").value("advanced"));

        verify(eciManagerClient).resetCloudResourceConfigSection("network");
        verify(eciManagerClient).resetCloudResourceConfigSection("oss");
        verify(eciManagerClient).resetCloudResourceConfigSection("advanced");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void fieldErrorFromManagerReturns400WithMessage() throws Exception {
        when(eciManagerClient.saveCloudResourceConfigSection("oss", Map.of("ECI_OSS_ENABLED", "yes")))
                .thenThrow(new BusinessException("启用 OSS：只能填写 true 或 false", HttpStatus.BAD_REQUEST));

        mockMvc.perform(put(PATH + "/oss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ECI_OSS_ENABLED":"yes"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("启用 OSS：只能填写 true 或 false"));
    }

    private static CloudResourceConfigView view(String vpcId) {
        return new CloudResourceConfigView(
                new CloudResourceConfigSection("network", "网络", List.of(
                        new CloudResourceConfigField(
                                "ECI_VPC_ID", "VPC", "", vpcId, "database", true, "TEXT", "任意文本")
                )),
                new CloudResourceConfigSection("oss", "OSS", List.of()),
                new CloudResourceConfigSection("advanced", "高级", List.of())
        );
    }
}
