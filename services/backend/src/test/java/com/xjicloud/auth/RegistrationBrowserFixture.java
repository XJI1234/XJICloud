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
                "--xjicloud.oss.endpoint=http://127.0.0.1:19000",
                "--xjicloud.oss.region=us-east-1", "--xjicloud.oss.bucket=registration-test",
                "--xjicloud.oss.access-key=test-only", "--xjicloud.oss.secret-key=test-only",
                "--xjicloud.admin.default-password=test-only-admin-password",
                "--xjicloud.sms.enabled=true",
                "--xjicloud.sms.access-key-id=test-only", "--xjicloud.sms.access-key-secret=test-only",
                "--xjicloud.sms.sign-name=test-only", "--xjicloud.sms.template-code=test-only"
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeSms {
        @Bean @Primary
        SmsSender browserSmsSender() {
            return new SmsSender() {
                @Override
                public void sendRegistrationCode(String phone, String challengeId) {
                    String code = "012345";
                    try {
                        Path file = Path.of("target/registration-browser-code.txt");
                        if (!Files.exists(file)) {
                            if (Files.getFileStore(file.toAbsolutePath().getParent()).supportsFileAttributeView("posix")) {
                                Files.createFile(file,
                                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                            } else {
                                Files.createFile(file);
                            }
                        }
                        Files.writeString(file, phone + ":" + code);
                    } catch (java.io.IOException ex) {
                        throw new IllegalStateException("Cannot capture browser test SMS");
                    }
                }
                @Override
                public boolean verifyRegistrationCode(String phone, String challengeId, String code) {
                    return "012345".equals(code);
                }
            };
        }
    }
}
