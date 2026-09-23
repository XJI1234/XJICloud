package com.xjicloud.admin;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xjicloud.admin.dto.CloudAccessKeyDtos.CloudAccessKeyListResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CloudAccessKeyController.class)
@Import(CloudAccessKeyControllerAuthTest.TestSecurityConfig.class)
@ContextConfiguration(classes = {CloudAccessKeyController.class, CloudAccessKeyControllerAuthTest.TestSecurityConfig.class})
class CloudAccessKeyControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CloudAccessKeyService cloudAccessKeyService;

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/cloud-access-keys"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/cloud-access-keys"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessList() throws Exception {
        when(cloudAccessKeyService.list("OSS"))
                .thenReturn(new CloudAccessKeyListResponse("OSS", null, List.of()));
        mockMvc.perform(get("/api/v1/admin/cloud-access-keys").queryParam("resource", "OSS"))
                .andExpect(status().isOk());
    }

    @EnableWebSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .build();
        }
    }
}
