package com.xjicloud.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.BusinessException;
import com.xjicloud.model.dto.DownloadTokenResponse;
import com.xjicloud.model.dto.ModelResponse;
import com.xjicloud.model.dto.ModelVersionResponse;
import com.xjicloud.model.dto.RestoreModelVersionRequest;
import com.xjicloud.model.dto.SaveViewerConfigRequest;
import com.xjicloud.model.dto.UploadChunkResponse;
import com.xjicloud.model.dto.UploadSessionResponse;
import com.xjicloud.model.dto.ViewerConfigResponse;
import com.xjicloud.project.Project;
import com.xjicloud.project.ProjectService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    private final ModelVersionRepository modelVersionRepository;
    private final ProjectService projectService;
    private final LocalFileStoreService localFileStoreService;
    private final ModelDownloadTokenService modelDownloadTokenService;
    private final ModelUploadSessionService modelUploadSessionService;
    private final ObjectMapper objectMapper;

    public ModelService(
            ModelAssetRepository modelAssetRepository,
            ViewerConfigRepository viewerConfigRepository,
            ModelVersionRepository modelVersionRepository,
            ProjectService projectService,
            LocalFileStoreService localFileStoreService,
            ModelDownloadTokenService modelDownloadTokenService,
            ModelUploadSessionService modelUploadSessionService,
            ObjectMapper objectMapper
    ) {
        this.modelAssetRepository = modelAssetRepository;
        this.viewerConfigRepository = viewerConfigRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.projectService = projectService;
        this.localFileStoreService = localFileStoreService;
        this.modelDownloadTokenService = modelDownloadTokenService;
        this.modelUploadSessionService = modelUploadSessionService;
        this.objectMapper = objectMapper;
    }

    public List<ModelResponse> listModels(UserAccount user, UUID projectId) {
        Project project = projectService.requireOwnedProject(user, projectId);
        return modelAssetRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public UploadSessionResponse createUploadSession(UserAccount user, UUID projectId, String fileName, long sizeBytes) {
        Project project = projectService.requireOwnedProject(user, projectId);
        String originalName = sanitizeFileName(fileName);
        ModelFormat format = detectFormat(originalName);
        if (sizeBytes < 1 || sizeBytes > ModelUploadSessionService.MAX_SIZE_BYTES) {
            throw new BusinessException("文件不能超过 2GB", HttpStatus.BAD_REQUEST);
        }
        try {
            UploadSessionMeta meta = modelUploadSessionService.create(
                    user.getId(),
                    project.getId(),
                    originalName,
                    ModelUploadSessionService.storedExtension(format),
                    sizeBytes
            );
            return toSessionResponse(meta);
        } catch (IOException ex) {
            throw new BusinessException("模型文件保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public UploadSessionResponse getUploadSession(UserAccount user, UUID sessionId) {
        return toSessionResponse(modelUploadSessionService.requireOwned(user.getId(), sessionId));
    }

    public UploadChunkResponse putUploadChunk(UserAccount user, UUID sessionId, String contentRange, InputStream body) {
        UploadSessionMeta meta = modelUploadSessionService.writeChunk(
                user.getId(),
                sessionId,
                ContentRange.parse(contentRange),
                body
        );
        return new UploadChunkResponse(meta.receivedBytes());
    }

    public void abortUploadSession(UserAccount user, UUID sessionId) {
        modelUploadSessionService.requireOwned(user.getId(), sessionId);
        modelUploadSessionService.deleteSession(user.getId(), sessionId);
    }

    @Transactional
    public ModelResponse completeUploadSession(UserAccount user, UUID sessionId) {
        UploadSessionMeta meta = modelUploadSessionService.requireOwned(user.getId(), sessionId);
        Optional<ModelAsset> existingAsset = modelAssetRepository.findById(meta.modelId());
        if (meta.completed() || existingAsset.isPresent()) {
            ModelAsset existing = existingAsset.orElseThrow(() -> new BusinessException("模型不存在", HttpStatus.NOT_FOUND));
            projectService.requireOwnedProject(user, existing.getProjectId());
            if (!meta.completed()) {
                modelUploadSessionService.markCompleted(user.getId(), sessionId);
            }
            return toResponse(existing);
        }
        if (meta.receivedBytes() != meta.sizeBytes()) {
            throw new BusinessException("文件尚未上传完成", HttpStatus.BAD_REQUEST);
        }
        Project project = projectService.requireOwnedProject(user, meta.projectId());
        ModelFormat format = detectFormat(meta.fileName());
        UUID modelId = meta.modelId();
        String storedFileName = "original." + format.name().toLowerCase(Locale.ROOT);
        Path payload = modelUploadSessionService.payloadPathFor(user.getId(), sessionId);
        Path storedPath = localFileStoreService.finalizeUploadedModel(user, project, modelId, payload, storedFileName);

        ModelAsset asset = new ModelAsset();
        asset.setId(modelId);
        asset.setProjectId(project.getId());
        asset.setFileName(meta.fileName());
        asset.setFormat(format);
        asset.setSizeBytes(localFileStoreService.fileSize(storedPath));
        asset.setStoragePath(localFileStoreService.toRelativeStoragePath(storedPath));
        asset.setVersion(1);
        modelAssetRepository.save(asset);
        syncViewerConfigEntity(asset, user, project);
        modelUploadSessionService.markCompleted(user.getId(), sessionId);
        return toResponse(asset);
    }

    @Transactional
    public void deleteModel(UserAccount user, UUID modelId) {
        ModelAsset asset = requireOwnedModel(user, modelId);
        Project project = projectService.requireOwnedProject(user, asset.getProjectId());
        viewerConfigRepository.deleteById(modelId);
        modelVersionRepository.deleteByModelId(modelId);
        modelAssetRepository.delete(asset);
        registerDeleteModelDirectoryAfterCommit(user, project, modelId);
    }

    private void registerDeleteModelDirectoryAfterCommit(UserAccount user, Project project, UUID modelId) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            localFileStoreService.deleteModelDirectory(user, project, modelId);
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        localFileStoreService.deleteModelDirectory(user, project, modelId);
                    }
                }
        );
    }

    private UploadSessionResponse toSessionResponse(UploadSessionMeta meta) {
        return new UploadSessionResponse(
                meta.sessionId(),
                ModelUploadSessionService.CHUNK_SIZE_BYTES,
                meta.receivedBytes(),
                meta.sizeBytes()
        );
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
                    .headers(noStoreHeaders())
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
                .headers(noStoreHeaders())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(asset.getFileName()))
                .contentLength(contentLength)
                .body(body);
    }

    public ResponseEntity<StreamingResponseBody> downloadVersionFile(
            UserAccount user,
            UUID modelId,
            UUID versionId
    ) throws IOException {
        requireOwnedModel(user, modelId);
        ModelVersionEntity version = modelVersionRepository.findByIdAndModelId(versionId, modelId)
                .orElseThrow(() -> new BusinessException("版本不存在", HttpStatus.NOT_FOUND));
        Path filePath = localFileStoreService.resolveStoredPath(version.getStoragePath());
        long fileSize = Files.size(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .headers(noStoreHeaders())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(version.getFileName()))
                .contentLength(fileSize)
                .body(outputStream -> Files.copy(filePath, outputStream));
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

    public ModelResponse exportModel(UserAccount user, UUID modelId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("导出文件不能为空");
        }

        ModelAsset asset = requireOwnedModel(user, modelId);
        Project project = projectService.requireOwnedProject(user, asset.getProjectId());
        return writeExport(user, project, asset, file);
    }

    ModelResponse writeExport(UserAccount user, Project project, ModelAsset asset, MultipartFile file) {
        String exportName = sanitizeFileName(file.getOriginalFilename());
        ExportTarget exportTarget = resolveExportTarget(exportName);
        UUID snapshotId = UUID.randomUUID();
        Path livePath = localFileStoreService.resolveStoredPath(asset.getStoragePath());

        try {
            // #region agent log
            debugExportLog("A", "ModelService.writeExport:beforeArchive", "live vs incoming size", asset, user, project, asset.getId(), file.getSize());
            // #endregion
            Path archivePath = localFileStoreService.archiveCurrentModel(user, project, asset.getId(), snapshotId, livePath);
            persistSnapshot(asset, snapshotId, archivePath);
            // #region agent log
            debugExportLog("B", "ModelService.writeExport:afterArchive", "archived previous live", asset, user, project, asset.getId(), file.getSize());
            // #endregion

            try (InputStream input = file.getInputStream()) {
                Path storedPath = localFileStoreService.replaceModelFile(
                        user,
                        project,
                        asset.getId(),
                        exportTarget.storedFileName(),
                        input
                );
                asset.setFileName(exportTarget.fileName());
                asset.setFormat(exportTarget.format());
                asset.setSizeBytes(localFileStoreService.fileSize(storedPath));
                asset.setStoragePath(localFileStoreService.toRelativeStoragePath(storedPath));
                asset.setVersion(asset.getVersion() + 1);
                asset.setUpdatedAt(Instant.now());
                modelAssetRepository.save(asset);
                return toResponse(asset);
            }
        } catch (IOException ex) {
            throw new BusinessException("导出保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public List<ModelVersionResponse> listModelVersions(UserAccount user, UUID modelId) {
        ModelAsset asset = requireOwnedModel(user, modelId);
        Project project = projectService.requireOwnedProject(user, asset.getProjectId());
        return listOwnedVersions(user, project, asset);
    }

    List<ModelVersionResponse> listOwnedVersions(UserAccount user, Project project, ModelAsset asset) {
        backfillLegacyExports(user, project, asset);

        List<ModelVersionResponse> versions = new ArrayList<>();
        versions.add(new ModelVersionResponse(
                "current",
                asset.getFileName(),
                asset.getSizeBytes(),
                asset.getUpdatedAt() != null ? asset.getUpdatedAt() : asset.getCreatedAt(),
                true,
                Math.max(1, asset.getVersion())
        ));
        for (ModelVersionEntity row : modelVersionRepository.findByModelIdOrderByVersionDesc(asset.getId())) {
            versions.add(new ModelVersionResponse(
                    row.getId().toString(),
                    row.getFileName(),
                    row.getSizeBytes(),
                    row.getCreatedAt(),
                    false,
                    row.getVersion()
            ));
        }
        // #region agent log
        debugExportLog("D", "ModelService.listModelVersions", "jpa version rows", asset, user, project, asset.getId(), -1);
        // #endregion
        return versions;
    }

    public ModelResponse restoreModelVersion(UserAccount user, UUID modelId, RestoreModelVersionRequest request) {
        ModelAsset asset = requireOwnedModel(user, modelId);
        Project project = projectService.requireOwnedProject(user, asset.getProjectId());
        return writeRestore(user, project, asset, request.versionId());
    }

    ModelResponse writeRestore(UserAccount user, Project project, ModelAsset asset, UUID versionId) {
        ModelVersionEntity snapshot = modelVersionRepository.findByIdAndModelId(versionId, asset.getId())
                .orElseThrow(() -> new BusinessException("版本不存在", HttpStatus.NOT_FOUND));
        Path snapshotPath = localFileStoreService.resolveStoredPath(snapshot.getStoragePath());
        Path livePath = localFileStoreService.resolveStoredPath(asset.getStoragePath());
        UUID backupId = UUID.randomUUID();
        // #region agent log
        debugExportLog("C", "ModelService.writeRestore:beforeBackup", "restore archives live first", asset, user, project, asset.getId(), localFileStoreService.fileSize(livePath));
        // #endregion
        Path backupPath = localFileStoreService.archiveCurrentModel(user, project, asset.getId(), backupId, livePath);
        persistSnapshot(asset, backupId, backupPath);

        String exportName = sanitizeFileName(snapshot.getFileName());
        ExportTarget exportTarget = resolveExportTarget(exportName);
        Path storedPath = localFileStoreService.copyToLive(user, project, asset.getId(), exportTarget.storedFileName(), snapshotPath);
        asset.setFileName(exportTarget.fileName());
        asset.setFormat(exportTarget.format());
        asset.setSizeBytes(localFileStoreService.fileSize(storedPath));
        asset.setStoragePath(localFileStoreService.toRelativeStoragePath(storedPath));
        asset.setVersion(asset.getVersion() + 1);
        asset.setUpdatedAt(Instant.now());
        modelAssetRepository.save(asset);
        return toResponse(asset);
    }

    private void persistSnapshot(ModelAsset asset, UUID snapshotId, Path archivePath) {
        if (modelVersionRepository.existsByModelIdAndVersion(asset.getId(), asset.getVersion())) {
            return;
        }
        ModelVersionEntity row = new ModelVersionEntity();
        row.setId(snapshotId);
        row.setModelId(asset.getId());
        row.setVersion(asset.getVersion());
        row.setFileName(asset.getFileName());
        row.setSizeBytes(localFileStoreService.fileSize(archivePath));
        row.setStoragePath(localFileStoreService.toRelativeStoragePath(archivePath));
        row.setCreatedAt(Instant.now());
        modelVersionRepository.save(row);
    }

    private void backfillLegacyExports(UserAccount user, Project project, ModelAsset asset) {
        if (modelVersionRepository.existsByModelId(asset.getId())) {
            return;
        }
        List<LocalFileStoreService.ExportArchive> archives = localFileStoreService.listExports(user, project, asset.getId());
        if (archives.isEmpty()) {
            return;
        }
        List<LocalFileStoreService.ExportArchive> oldestFirst = new ArrayList<>(archives);
        java.util.Collections.reverse(oldestFirst);
        int versionNo = 1;
        for (LocalFileStoreService.ExportArchive archive : oldestFirst) {
            Path path = localFileStoreService.resolveExportPath(user, project, asset.getId(), archive.archiveName());
            ModelVersionEntity row = new ModelVersionEntity();
            row.setId(UUID.randomUUID());
            row.setModelId(asset.getId());
            row.setVersion(versionNo);
            row.setFileName(archive.fileName());
            row.setSizeBytes(archive.sizeBytes());
            row.setStoragePath(localFileStoreService.toRelativeStoragePath(path));
            row.setCreatedAt(archive.createdAt());
            modelVersionRepository.save(row);
            versionNo += 1;
        }
    }

    private static HttpHeaders noStoreHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store, no-cache, must-revalidate");
        headers.setPragma("no-cache");
        return headers;
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

    // #region agent log
    private void debugExportLog(
            String hypothesisId,
            String location,
            String message,
            ModelAsset asset,
            UserAccount user,
            Project project,
            UUID modelId,
            long incomingLen
    ) {
        try {
            Path livePath = localFileStoreService.modelFilePath(user, project, modelId, "original.ply");
            if (!Files.exists(livePath)) {
                livePath = localFileStoreService.modelFilePath(user, project, modelId, "original.spz");
            }
            long liveSize = Files.exists(livePath) ? Files.size(livePath) : -1L;
            String livePrefix = peekPrefix(livePath);
            var archives = localFileStoreService.listExports(user, project, modelId);
            long newestArchiveSize = archives.isEmpty() ? -1L : archives.get(0).sizeBytes();
            String newestName = archives.isEmpty() ? "" : archives.get(0).archiveName().replace("\"", "");
            String archivePrefix = "";
            if (!archives.isEmpty()) {
                archivePrefix = peekPrefix(localFileStoreService.resolveExportPath(user, project, modelId, newestName));
            }
            String data = "{\"assetVersion\":" + asset.getVersion()
                    + ",\"liveSize\":" + liveSize
                    + ",\"livePrefix\":\"" + livePrefix.replace("\"", "")
                    + "\",\"incomingLen\":" + incomingLen
                    + ",\"archiveCount\":" + archives.size()
                    + ",\"newestArchiveSize\":" + newestArchiveSize
                    + ",\"archivePrefix\":\"" + archivePrefix.replace("\"", "")
                    + "\",\"newestArchiveName\":\"" + newestName
                    + "\"}";
            String line = "{\"sessionId\":\"14ec0c\",\"runId\":\"post-fix\",\"hypothesisId\":\"" + hypothesisId
                    + "\",\"location\":\"" + location + "\",\"message\":\"" + message
                    + "\",\"data\":" + data + ",\"timestamp\":" + System.currentTimeMillis() + "}\n";
            Files.writeString(
                    Path.of("d:/WeChatProjects/XJICloud/debug-14ec0c.log"),
                    line,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    private static String peekPrefix(Path path) {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[16];
            int n = in.read(buf);
            if (n <= 0) {
                return "";
            }
            return new String(buf, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1).replace("\"", "").replace("\n", "");
        } catch (Exception ex) {
            return "";
        }
    }
    // #endregion

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
