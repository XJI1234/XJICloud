package com.xjicloud.admin;

import com.xjicloud.config.AdminProperties;
import java.util.Optional;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminDataInitializer implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final AdminProperties adminProperties;
    private final PasswordEncoder passwordEncoder;

    public AdminDataInitializer(
            AdminUserRepository adminUserRepository,
            AdminProperties adminProperties,
            PasswordEncoder passwordEncoder
    ) {
        this.adminUserRepository = adminUserRepository;
        this.adminProperties = adminProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String username = adminProperties.defaultUsername().trim();
        String password = adminProperties.defaultPassword();

        Optional<AdminUser> existing = adminUserRepository.findByUsername(username);
        if (existing.isEmpty()) {
            AdminUser admin = new AdminUser();
            admin.setUsername(username);
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setRole("ADMIN");
            adminUserRepository.save(admin);
            return;
        }

        if (adminProperties.syncPasswordOnStartup()) {
            AdminUser admin = existing.get();
            admin.setPasswordHash(passwordEncoder.encode(password));
            adminUserRepository.save(admin);
        }
    }
}
