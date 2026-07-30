package com.xjicloud.admin;

import com.xjicloud.admin.dto.DashboardResponse;
import com.xjicloud.admin.dto.OssConfigResponse;
import com.xjicloud.admin.dto.UpdateOssConfigRequest;
import com.xjicloud.common.ApiResponse;
import com.xjicloud.job.dto.JobResponse;
import com.xjicloud.worker.dto.WorkerResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.ok(adminService.getDashboard());
    }

    @GetMapping("/oss")
    public ApiResponse<OssConfigResponse> getOssConfig() {
        return ApiResponse.ok(adminService.getOssConfig());
    }

    @PutMapping("/oss")
    public ApiResponse<OssConfigResponse> updateOssConfig(
            @AuthenticationPrincipal AdminUser admin,
            @Valid @RequestBody UpdateOssConfigRequest request
    ) {
        return ApiResponse.ok(adminService.updateOssConfig(request, admin.getUsername()));
    }

    @PostMapping("/oss/test")
    public ApiResponse<Void> testOssConnection() {
        adminService.testOssConnection();
        return ApiResponse.ok("OSS 连接成功", null);
    }

    @GetMapping("/workers")
    public ApiResponse<List<WorkerResponse>> listWorkers() {
        return ApiResponse.ok(adminService.listWorkers());
    }

    @PostMapping("/workers/{workerId}/offline")
    public ApiResponse<Void> forceOffline(@PathVariable UUID workerId) {
        adminService.forceOfflineWorker(workerId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/jobs")
    public ApiResponse<List<JobResponse>> listJobs() {
        return ApiResponse.ok(adminService.listJobs());
    }

    @PostMapping("/jobs/{jobId}/retry")
    public ApiResponse<JobResponse> retryJob(@PathVariable UUID jobId) {
        return ApiResponse.ok(adminService.retryJob(jobId));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ApiResponse<JobResponse> cancelJob(@PathVariable UUID jobId) {
        return ApiResponse.ok(adminService.cancelJob(jobId));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        return ApiResponse.ok(Map.of(
                "users", adminService.countUsers(),
                "projects", adminService.countProjects()
        ));
    }
}
