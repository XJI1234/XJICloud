package com.xjicloud.framework.deploy;

import com.xjicloud.framework.auth.FrameworkUser;
import com.xjicloud.framework.common.ApiResponse;
import com.xjicloud.framework.config.MasterOnly;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@MasterOnly
@RequestMapping("/api/v1/deploy")
public class DeployController {

    private final DeployService deployService;

    public DeployController(DeployService deployService) {
        this.deployService = deployService;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<DeployService.DeployTaskView>> listTasks() {
        return ApiResponse.ok(deployService.listTasks());
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<DeployService.DeployTaskView> getTask(@PathVariable UUID id) {
        return ApiResponse.ok(deployService.getTask(id));
    }

    @PostMapping("/tasks")
    public ApiResponse<DeployService.DeployTaskView> start(
            @AuthenticationPrincipal FrameworkUser user,
            @RequestBody DeployService.StartDeployRequest request
    ) {
        return ApiResponse.ok(deployService.startDeploy(request, user.getUsername()));
    }
}
