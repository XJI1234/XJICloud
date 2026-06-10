package com.xjicloud.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.BusinessException;
import com.xjicloud.model.dto.DownloadTokenResponse;
import com.xjicloud.model.dto.ModelResponse;
import com.xjicloud.model.dto.SaveViewerConfigRequest;
import com.xjicloud.model.dto.ViewerConfigResponse;
import com.xjicloud.project.Project;
import com.xjicloud.project.ProjectService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
public class ModelService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("ply", "spz");

    private final ModelAssetRepository modelAssetRepository;
    private final ViewerConfigRepository viewerConfigRepository;
    private final ProjectService projectService;
    private final LocalFileStoreService localFileStoreService;
    private final ModelDownloadTokenService modelDownloadTokenService;
    private final ObjectMapper objectMapper;

    public ModelService(
            ModelAssetRepository modelAssetRepository,
            ViewerConfigRepository viewerConfigRepository,
            ProjectService projectService,
            LocalFileStoreService localFileStoreService,
            ModelDownloadTokenService modelDownloadTokenService,
            ObjectMapper objectMapper
    ) {
        this.modelAssetRepository = modelAssetRepository;
        this.viewerConfigRepository = viewerConfigRepository;
        this.projectService = projectService;
        this.localFileStoreService = localFileStoreService;
        this.modelDownloadTokenService = modelDownloadTokenService;
        this.objectMapper = objectMapper;
    }

    public List<ModelResponse> listModels(UserAccount user, UUID projectId) {
        Project project = projectService.requireOwnedProject(user, projectId);
        return modelAssetRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ModelResponse uploadModel(UserAccount user, UUID projectId, MultipartFile file) {
        Project project = projectService.requireOwnedProject(user, projectId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalName = sanitizeFileName(file.getOriginalFilename());
        ModelFormat format = detectFormat(originalName);
        UUID modelId = UUID.randomUUID();
        String storedFileName = "original." + format.name().toLowerCase(Locale.ROOT);

        localFileStoreService.storeUploadedModel(user, project, modelId, file, storedFileName);
        Path storedPath = localFileStoreService.modelFilePath(user, project, modelId, storedFileName);

        ModelAsset asset = new ModelAsset();
        asset.setId(modelId);
        asset.setProjectId(project.getId());
        asset.setFileName(originalName);
        asset.setFormat(format);
        asset.setSizeBytes(localFileStoreService.fileSize(storedPath));
        asset.setStoragePath(localFileStoreService.toRelativeStoragePath(storedPath));
        asset.setVersion(1);
        modelAssetRepository.save(asset);

        syncViewerConfigEntity(asset, user, project);
        return toResponse(asset);
    }

    public ModelAsset requireOwnedModel(UserAccount user, UUID modelId) {
        ModelAsset asset = modelAssetRepository.findById(modelId)
                .orElseThrow(() -> new BusinessException("模型不存在", HttpStatus.NOT_FOUND));
        projectService.requireOwnedProject(user, asset.getProjectId());
        return asset;
    }

    public DownloadTokenResponse createDownloadToken(UserAccount user, UUID modelId, HttpServletRequest request) {
        requireOwnedModel(user, modelId);
        ModelDownloadTokenService.DownloadTokenDetails details = modelDownloadTokenService.createToken(user, modelId);
        String url = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath("/api/v1/models/{modelId}/download")
                .replaceQueryParam("access_token", details.token())
                .buildAndExpand(modelId)
                .toUriString();
        return new DownloadTokenResponse(url, details.expiresAt());
    }

    public ResponseEntity<StreamingResponseBody> downloadModel(
            UserAccount user,
            UUID modelId,
            String accessToken,
            String rangeHeader
    ) throws IOException {
        ModelAsset asset = resolveDownloadAsset(user, modelId, accessToken);
        Path filePath = localFileStoreService.resolveStoredPath(asset.getStoragePath());
        long fileSize = Files.size(filePath);
        MediaType mediaType = asset.getFormat() == ModelFormat.SPZ
                ? MediaType.parseMediaType("application/octet-stream")
                : MediaType.parseMediaType("application/octet-stream");

        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            Resource resource = new UrlResource(filePath.toUri());
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(asset.getFileName()))
                    .contentLength(fileSize)
                    .body(outputStream -> Files.copy(filePath, outputStream));
        }

        long[] range = parseRange(rangeHeader, fileSize);
        long start = range[0];
        long end = range[1];
        long contentLength = end - start + 1;

        StreamingResponseBody body = outputStream -> localFileStoreService.copyRange(filePath, outputStream, start, end);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(asset.getFileName()))
                .contentLength(contentLength)
                .body(body);
    }

    public ViewerConfigResponse getViewerConfig(UserAccount user, UUID modelId) {
        ModelAsset asset = requireOwnedModel(user, modelId);
        Project project = projectService.requireOwnedProject(user, asset.getProjectId());
        ViewerConfigEntity entity = viewerConfigRepository.findById(modelId).orElseGet(() -> {
            syncViewerConfigEntity(asset, user, project);
            return viewerConfigRepository.findById(modelId).orElseThrow();
        });
        return new ViewerConfigResponse(entity.getJsonPayload(), entity.getUpdatedAt());
    }

    @Transactional
    public ViewerConfigResponse saveViewerConfig(UserAccount user, UUID modelId, SaveViewerConfigRequest request) {
        ModelAsset asset = requireOwnedModel(user, modelId);
        Project project = projectService.requireOwnedProject(user, asset.getProjectId());
        validateViewerConfigJson(request.jsonPayload());

        localFileStoreService.writeViewerConfig(user, project, modelId, request.jsonPayload());

        ViewerConfigEntity entity = viewerConfigRepository.findById(modelId).orElseGet(ViewerConfigEntity::new);
        entity.setModelId(modelId);
        entity.setJsonPayload(request.jsonPayload());
        entity.setUpdatedAt(Instant.now());
        viewerConfigRepository.save(entity);

        return new ViewerConfigResponse(entity.getJsonPayload(), entity.getUpdatedAt());
    }

    @Transactional
    public ModelResponse exportModel(UserAccount user, UUID modelId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("导出文件不能为空");
        }

        ModelAsset asset = requireOwnedModel(user, modelId);
        Project project = projectService.requireOwnedProject(user, asset.getProjectId());

        String exportName = sanitizeFileName(file.getOriginalFilename());
        ExportTarget exportTarget = resolveExportTarget(exportName);

        try {
            byte[] bytes = file.getBytes();
            localFileStoreService.storeExport(user, project, modelId, exportTarget.fileName(), bytes);

            localFileStoreService.replaceModelFile(user, project, modelId, exportTarget.storedFileName(), bytes);
            Path storedPath = localFileStoreService.modelFilePath(user, project, modelId, exportTarget.storedFileName());

            asset.setFileName(exportTarget.fileName());
            asset.setFormat(exportTarget.format());
            asset.setSizeBytes(bytes.length);
            asset.setStoragePath(localFileStoreService.toRelativeStoragePath(storedPath));
            asset.setVersion(asset.getVersion() + 1);
            asset.setUpdatedAt(Instant.now());
            modelAssetRepository.save(asset);
            return toResponse(asset);
        } catch (IOException ex) {
            throw new BusinessException("导出保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void syncViewerConfigEntity(ModelAsset asset, UserAccount user, Project project) {
        String json = localFileStoreService.readViewerConfig(user, project, asset.getId());
        ViewerConfigEntity entity = viewerConfigRepository.findById(asset.getId()).orElseGet(ViewerConfigEntity::new);
        entity.setModelId(asset.getId());
        entity.setJsonPayload(json);
        entity.setUpdatedAt(Instant.now());
        viewerConfigRepository.save(entity);
    }

    private void validateViewerConfigJson(String jsonPayload) {
        try {
            JsonNode node = objectMapper.readTree(jsonPayload);
            if (!node.isObject()) {
                throw new BusinessException("查看器配置必须是 JSON 对象");
            }
        } catch (IOException ex) {
            throw new BusinessException("查看器配置 JSON 无效");
        }
    }

    private long[] parseRange(String rangeHeader, long fileSize) {
        String value = rangeHeader.substring("bytes=".length()).trim();
        String[] parts = value.split("-", 2);
        long start = Long.parseLong(parts[0]);
        long end = parts.length > 1 && !parts[1].isBlank() ? Long.parseLong(parts[1]) : fileSize - 1;
        if (start < 0 || end >= fileSize || start > end) {
            throw new BusinessException("无效的 Range 请求", HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        return new long[] { start, end };
    }

    private String contentDisposition(String fileName) {
        return "attachment; filename=\"" + fileName.replace("\"", "") + "\"";
    }

    private ModelAsset resolveDownloadAsset(UserAccount user, UUID modelId, String accessToken) {
        if (user != null) {
            return requireOwnedModel(user, modelId);
        }

        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException("未授权", HttpStatus.UNAUTHORIZED);
        }

        UUID userId = modelDownloadTokenService.validateToken(accessToken, modelId);
        ModelAsset asset = modelAssetRepository.findById(modelId)
                .orElseThrow(() -> new BusinessException("模型不存在", HttpStatus.NOT_FOUND));
        projectService.requireOwnedProjectByUserId(userId, asset.getProjectId());
        return asset;
    }

    private ExportTarget resolveExportTarget(String exportName) {
        String lower = exportName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ply")) {
            return new ExportTarget(exportName, "original.ply", ModelFormat.PLY);
        }
        if (lower.endsWith(".spz")) {
            return new ExportTarget(exportName, "original.spz", ModelFormat.SPZ);
        }

        throw new BusinessException("导出文件必须是 PLY 或 SPZ");
    }

    private record ExportTarget(String fileName, String storedFileName, ModelFormat format) {
    }

    private ModelFormat detectFormat(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".spz")) {
            return ModelFormat.SPZ;
        }
        if (lower.endsWith(".ply")) {
            return ModelFormat.PLY;
        }
        throw new BusinessException("仅支持 PLY 或 SPZ 文件");
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException("文件名无效");
        }
        String sanitized = Path.of(fileName).getFileName().toString().trim();
        String lower = sanitized.toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
        if (!allowed) {
            throw new BusinessException("仅支持 PLY 或 SPZ 文件");
        }
        return sanitized;
    }

    private ModelResponse toResponse(ModelAsset asset) {
        return new ModelResponse(
                asset.getId(),
                asset.getProjectId(),
                asset.getFileName(),
                asset.getFormat(),
                asset.getSizeBytes(),
                asset.getVersion(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
