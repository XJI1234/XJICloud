package com.xjicloud.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xjicloud.admin.dto.CloudAccessKeyDtos.CloudAccessKeyResponse;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.CreateCloudAccessKeyRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

@WebMvcTest(CloudAccessKeyController.class)
@Import(CloudAccessKeyControllerAuthTest.TestSecurityConfig.class)
@ContextConfiguration(classes = {
        CloudAccessKeyController.class,
        CloudAccessKeyControllerAuthTest.TestSecurityConfig.class
})
class CloudAccessKeyLabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CloudAccessKeyService cloudAccessKeyService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createAcceptsLabelAndGetReturnsItWithoutSecret() throws Exception {
        UUID id = UUID.randomUUID();
        CloudAccessKeyResponse response = new CloudAccessKeyResponse(
                id,
                "training",
                "LT****01",
                true,
                List.of("OSS"),
                List.of(),
                Instant.parse("2026-09-19T12:00:00Z"),
                Instant.parse("2026-09-19T12:00:00Z"),
                "training"
        );
        when(cloudAccessKeyService.create(any(CreateCloudAccessKeyRequest.class))).thenReturn(response);
        when(cloudAccessKeyService.get(id)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/cloud-access-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"training","accessKeyId":"LTAItestkey0001",
                                 "accessKeySecret":"test-secret-value-not-real","resources":["OSS"],
                                 "label":"  training  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.label").value("training"))
                .andExpect(jsonPath("$.data.accessKeySecret").doesNotExist());

        ArgumentCaptor<CreateCloudAccessKeyRequest> request =
                ArgumentCaptor.forClass(CreateCloudAccessKeyRequest.class);
        org.mockito.Mockito.verify(cloudAccessKeyService).create(request.capture());
        assertEquals("  training  ", request.getValue().label());

        mockMvc.perform(get("/api/v1/admin/cloud-access-keys/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.label").value("training"))
                .andExpect(jsonPath("$.data.accessKeySecret").doesNotExist());
    }
}
