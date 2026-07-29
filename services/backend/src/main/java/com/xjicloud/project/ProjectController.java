package com.xjicloud.project;

import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.ApiResponse;
import com.xjicloud.project.dto.CreateProjectRequest;
import com.xjicloud.project.dto.ProjectResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(@AuthenticationPrincipal UserAccount user) {
        return ApiResponse.ok(projectService.listProjects(user));
    }

    @PostMapping
    public ApiResponse<ProjectResponse> create(
            @AuthenticationPrincipal UserAccount user,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return ApiResponse.ok(projectService.createProject(user, request));
    }
}
