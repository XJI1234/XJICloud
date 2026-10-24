package com.xjicloud.eci.config;

import com.xjicloud.eci.provider.AliyunEciProvider;
import com.xjicloud.eci.provider.EciProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实模式启动顺序回归：数据库网络覆盖必须在 {@code EciProvider} 创建前生效。
 * 覆盖值只存在于数据库，apply 之前 {@code ProviderConfig} 的网络校验必然失败；不联网。
 * 用例只依赖公开入口（数据库行 + Provider/属性 Bean），不新增生产钩子。
 */
@SpringBootTest(properties = {
        "eci.mock=false",
        "eci.api-key=test-api-key",
        "eci.access-key-id=LTAIstartuporder01",
        "eci.access-key-secret=startup-order-secret",
        "eci.console.open-browser=false",
        "spring.datasource.url=" + CloudResourceConfigStartupOrderingTest.JDBC_URL
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = CloudResourceConfigStartupOrderingTest.NetworkOverrideSeeder.class)
class CloudResourceConfigStartupOrderingTest {

    static final String JDBC_URL = "jdbc:h2:mem:eci-config-startup-ordering;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private static final String PATH = "/api/v1/eci/runtime-config";
    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EciProvider eciProvider;

    @Autowired
    private EciProperties eciProperties;

    @Autowired
    private Environment environment;

    @Test
    void realModeProviderIsCreatedAfterDatabaseNetworkOverrideIsRead() throws Exception {
        assertThat(environment.getProperty("ECI_VSWITCH_ID"))
                .as("本机导出了 ECI_VSWITCH_ID，无法区分数据库覆盖与进程配置")
                .isNull();

        assertThat(eciProvider).isInstanceOf(AliyunEciProvider.class);
        assertThat(eciProperties.getRegionId()).isEqualTo("cn-shanghai");
        assertThat(eciProperties.getVpcId()).isEqualTo("vpc-startup-override");
        assertThat(eciProperties.getVswitchId()).isEqualTo("vsw-startup-override");
        assertThat(eciProperties.getSecurityGroupId()).isEqualTo("sg-startup-override");

        mockMvc.perform(get(PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.network.fields[0].value").value("cn-shanghai"))
                .andExpect(jsonPath("$.data.network.fields[0].source").value("database"))
                .andExpect(jsonPath("$.data.network.fields[2].value").value("vsw-startup-override"))
                .andExpect(jsonPath("$.data.network.fields[2].source").value("database"));
    }

    /** 刷新前写入数据库覆盖，使启动顺序成为唯一可解释的通过条件。 */
    static class NetworkOverrideSeeder implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "")) {
                new ResourceDatabasePopulator(new ClassPathResource("cloud-resource-config-network-seed.sql"))
                        .populate(connection);
            } catch (SQLException ex) {
                throw new IllegalStateException("无法预置数据库网络覆盖", ex);
            }
        }
    }
}
