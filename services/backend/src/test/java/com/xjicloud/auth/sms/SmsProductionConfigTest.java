package com.xjicloud.auth.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import com.xjicloud.config.OssProperties;
import static org.junit.jupiter.api.Assertions.*;

class SmsProductionConfigTest {
    @TempDir Path directory;

    @Test
    void existingProductionTemplateLoadsWithProdProfileAndBindsTheOnlySmsProvider() throws Exception {
        Path config = directory.resolve("application-prod.yml");
        Files.copy(Path.of("../../deploy/config/application-prod.yml.example"), config);
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Bindings.class, AliyunPnvsSmsSender.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("spring.profiles.active=prod", "spring.config.location=" + config.toUri())
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var sms = context.getBean(SmsProperties.class);
                    assertFalse(sms.isEnabled());
                    assertEquals("aliyun-pnvs", sms.getProvider());
                    assertEquals("xjicloud-register", sms.getSchemeName());
                    assertEquals(1000, sms.getDailyLimit());
                    assertEquals(1, context.getBeansOfType(SmsSender.class).size());
                });
    }

    @Test
    void productionYamlActuallyBindsChangedNumbersBooleansAndEscapedPasswords() throws Exception {
        Path config = directory.resolve("application-prod.yml");
        String password = "db\"\\password: #测试";
        String template = Files.readString(Path.of("../../deploy/config/application-prod.yml.example"))
                .replace("daily-limit: 1000", "daily-limit: 37")
                .replace("scheme-name: xjicloud-register", "scheme-name: yaml-binding-test")
                .replace("    password: change-me", "    password: " + new ObjectMapper().writeValueAsString(password));
        Files.writeString(config, template);
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Bindings.class)
                .withPropertyValues("spring.profiles.active=prod", "spring.config.location=" + config.toUri())
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(37, context.getBean(SmsProperties.class).getDailyLimit());
                    assertEquals("yaml-binding-test", context.getBean(SmsProperties.class).getSchemeName());
                    assertEquals(password, context.getBean(DataSourceProperties.class).getPassword());
                    assertTrue(context.getBean(OssProperties.class).pathStyleAccess());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({SmsProperties.class, DataSourceProperties.class, OssProperties.class})
    static class Bindings {}
}
