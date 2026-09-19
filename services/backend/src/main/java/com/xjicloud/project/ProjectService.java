package com.xjicloud.project;

import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.BusinessException;
import com.xjicloud.job.DatasetAssetRepository;
import com.xjicloud.job.TrainingJob;
import com.xjicloud.job.TrainingJobRepository;
import com.xjicloud.model.LocalFileStoreService;
import com.xjicloud.model.ModelAsset;
import com.xjicloud.model.ModelAssetRepository;
import com.xjicloud.model.ModelUploadSessionService;
import com.xjicloud.model.ViewerConfigRepository;
import com.xjicloud.project.dto.CreateProjectRequest;
import com.xjicloud.project.dto.ProjectResponse;
import com.xjicloud.project.dto.UpdateProjectRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ModelAssetRepository modelAssetRepository;
    private final ViewerConfigRepository viewerConfigRepository;
    private final TrainingJobRepository trainingJobRepository;
    private final DatasetAssetRepository datasetAssetRepository;
    private final LocalFileStoreService localFileStoreService;
    private final ModelUploadSessionService modelUploadSessionService;

    public ProjectService(
            ProjectRepository projectRepository,
            ModelAssetRepository modelAssetRepository,
            ViewerConfigRepository viewerConfigRepository,
            TrainingJobRepository trainingJobRepository,
            DatasetAssetRepository datasetAssetRepository,
            LocalFileStoreService localFileStoreService,
            ModelUploadSessionService modelUploadSessionService
    ) {
        this.projectRepository = projectRepository;
        this.modelAssetRepository = modelAssetRepository;
        this.viewerConfigRepository = viewerConfigRepository;
        this.trainingJobRepository = trainingJobRepository;
        this.datasetAssetRepository = datasetAssetRepository;
        this.localFileStoreService = localFileStoreService;
        this.modelUploadSessionService = modelUploadSessionService;
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

    @Transactional
    public ProjectResponse updateProject(UserAccount user, UUID projectId, UpdateProjectRequest request) {
        Project project = requireOwnedProject(user, projectId);
        if (request.name() != null) {
            String trimmedName = request.name().trim();
            if (trimmedName.isBlank()) {
                throw new BusinessException("工程名称不能为空", HttpStatus.BAD_REQUEST);
            }
            project.setName(trimmedName);
        }
        if (request.description() != null) {
            project.setDescription(request.description().trim());
        }
        projectRepository.save(project);
        return toResponse(project);
    }

    /**
     * 删除工程：级联清理模型、viewer 配置、训练任务、数据集记录与磁盘文件。
     *
     * FIXME(H1): GPU Worker 取消/停任务与 OSS 级联删除的落地形态尚未确认，当前不停止
     * Redis 队列中的任务、不中断 RUNNING Worker、也不删除 OSS 上 datasets/outputs。
     * 待 GPU 任务生命周期确定后再补齐。
     */
    @Transactional
    public void deleteProject(UserAccount user, UUID projectId) {
        Project project = requireOwnedProject(user, projectId);

        List<ModelAsset> models = modelAssetRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        for (ModelAsset model : models) {
            viewerConfigRepository.deleteById(model.getId());
        }
        modelAssetRepository.deleteByProjectId(projectId);

        List<TrainingJob> jobs = trainingJobRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        for (TrainingJob job : jobs) {
            datasetAssetRepository.deleteByJobId(job.getId());
        }
        trainingJobRepository.deleteByProjectId(projectId);

        // Disk cleanup runs after DB commit (see afterCommit registration below).
        registerDeleteProjectDirectoryAfterCommit(user, project);
        projectRepository.delete(project);
    }

    private void registerDeleteProjectDirectoryAfterCommit(UserAccount user, Project project) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            localFileStoreService.deleteProjectDirectory(user, project);
            modelUploadSessionService.deleteSessionsForProject(user.getId(), project.getId());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                localFileStoreService.deleteProjectDirectory(user, project);
                modelUploadSessionService.deleteSessionsForProject(user.getId(), project.getId());
            }
        });
    }

    public Project requireOwnedProject(UserAccount user, UUID projectId) {
        return requireOwnedProjectByUserId(user.getId(), projectId);
    }

    public Project requireOwnedProjectByUserId(UUID userId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在", HttpStatus.NOT_FOUND));
        if (!project.getOwnerId().equals(userId)) {
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
