package com.xjicloud.auth;

import com.xjicloud.XjiCloudApplication;
import com.xjicloud.auth.sms.SmsSender;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Browser-only test server: real controllers, JWT, database and Redis; fake SMS transport.
 * Not packaged in the production jar. Never run on a public interface or use real credentials.
 */
public class RegistrationBrowserFixture {
    public static void main(String[] args) {
        SpringApplication.run(new Class<?>[]{XjiCloudApplication.class, FakeSms.class}, new String[]{
                "--server.address=127.0.0.1", "--server.port=18081",
                "--spring.datasource.url=jdbc:h2:mem:registration-browser;DB_CLOSE_DELAY=-1",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.data.redis.host=127.0.0.1", "--spring.data.redis.port=16379",
                "--xjicloud.storage.root=./target/registration-browser-storage",
                "--xjicloud.sms.enabled=true",
                "--xjicloud.sms.access-key-id=test-only", "--xjicloud.sms.access-key-secret=test-only",
                "--xjicloud.sms.sign-name=test-only", "--xjicloud.sms.template-code=test-only",
                "--xjicloud.sms.code-secret=browser-test-only-hmac-secret-at-least-32-characters"
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeSms {
        @Bean @Primary
        SmsSender browserSmsSender() {
            return (phone, code) -> {
                try {
                    Path file = Path.of("target/registration-browser-code.txt");
                    if (!Files.exists(file)) Files.createFile(file,
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                    Files.writeString(file, phone + ":" + code);
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Cannot capture browser test SMS");
                }
            };
        }
    }
}
