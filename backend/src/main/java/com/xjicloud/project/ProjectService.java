package com.xjicloud.project;

import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.BusinessException;
import com.xjicloud.project.dto.CreateProjectRequest;
import com.xjicloud.project.dto.ProjectResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<ProjectResponse> listProjects(UserAccount user) {
        return projectRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectResponse createProject(UserAccount user, CreateProjectRequest request) {
        Project project = new Project();
        project.setOwnerId(user.getId());
        project.setName(request.name().trim());
        project.setDescription(request.description() != null ? request.description().trim() : "");
        projectRepository.save(project);
        return toResponse(project);
    }

    public Project requireOwnedProject(UserAccount user, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在", HttpStatus.NOT_FOUND));
        if (!project.getOwnerId().equals(user.getId())) {
            throw new BusinessException("无权访问该项目", HttpStatus.FORBIDDEN);
        }
        return project;
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt()
        );
    }
}
