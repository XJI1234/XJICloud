package com.xjicloud.auth.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class SmsProductionConfigTest {
    @TempDir Path directory;

    @Test
    void existingProductionTemplateLoadsWithProdProfileAndBindsTheOnlySmsProvider() throws Exception {
        Path config = directory.resolve("application-prod.yml");
        Files.copy(Path.of("../../deploy/config/application-prod.yml.example"), config);
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(SmsProperties.class, AliyunPnvsSmsSender.class)
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
}
