package com.xjicloud.admin;

import com.xjicloud.admin.dto.DashboardResponse;
import com.xjicloud.admin.dto.OssConfigResponse;
import com.xjicloud.admin.dto.UpdateOssConfigRequest;
import com.xjicloud.auth.UserAccountRepository;
import com.xjicloud.config.AdminProperties;
import com.xjicloud.job.JobStatus;
import com.xjicloud.job.TrainingJobRepository;
import com.xjicloud.job.TrainingJobService;
import com.xjicloud.job.dto.JobResponse;
import com.xjicloud.oss.OssStorageService;
import com.xjicloud.project.ProjectRepository;
import com.xjicloud.queue.RedisQueueService;
import com.xjicloud.worker.WorkerService;
import com.xjicloud.worker.WorkerStatus;
import com.xjicloud.worker.dto.WorkerResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
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
        if (adminUserRepository.findByUsername(adminProperties.defaultUsername()).isEmpty()) {
            AdminUser admin = new AdminUser();
            admin.setUsername(adminProperties.defaultUsername());
            admin.setPasswordHash(passwordEncoder.encode(adminProperties.defaultPassword()));
            admin.setRole("ADMIN");
            adminUserRepository.save(admin);
        }
    }
}
