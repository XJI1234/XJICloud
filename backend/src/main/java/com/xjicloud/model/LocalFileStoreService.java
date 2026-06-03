package com.xjicloud.model;

import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.StorageProperties;
import com.xjicloud.project.Project;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStoreService {

    private static final String DEFAULT_VIEWER_CONFIG = """
            {
              "version": 2,
              "defaultView": null,
              "pointAnnotations": [],
              "cubeMarkers": [],
              "projectInfo": {
                "projectName": "",
                "fields": [
                  { "key": "coordinates", "label": "经纬度", "value": "" },
                  { "key": "buildingName", "label": "建筑名称", "value": "" },
                  { "key": "floorCount", "label": "楼层数", "value": "" },
                  { "key": "height", "label": "高度", "value": "" }
                ]
              }
            }
            """;

    private final StorageProperties storageProperties;
    private Path storageRoot;

    public LocalFileStoreService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @PostConstruct
    void init() throws IOException {
        storageRoot = Path.of(storageProperties.root()).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
    }

    public Path modelDirectory(UserAccount user, Project project, UUID modelId) {
        return storageRoot
                .resolve("users")
                .resolve(user.getId().toString())
                .resolve("projects")
                .resolve(project.getId().toString())
                .resolve("models")
                .resolve(modelId.toString());
    }

    public Path modelFilePath(UserAccount user, Project project, UUID modelId, String fileName) {
        return modelDirectory(user, project, modelId).resolve(fileName);
    }

    public Path viewerConfigPath(UserAccount user, Project project, UUID modelId) {
        return modelDirectory(user, project, modelId).resolve("viewer.json");
    }

    public Path exportsDirectory(UserAccount user, Project project, UUID modelId) {
        return modelDirectory(user, project, modelId).resolve("exports");
    }

    public void storeUploadedModel(UserAccount user, Project project, UUID modelId, MultipartFile file, String storedFileName) {
        try {
            Path directory = modelDirectory(user, project, modelId);
            Files.createDirectories(directory);
            Path target = directory.resolve(storedFileName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Path viewerConfig = viewerConfigPath(user, project, modelId);
            if (!Files.exists(viewerConfig)) {
                Files.writeString(viewerConfig, DEFAULT_VIEWER_CONFIG, StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException ex) {
            throw new BusinessException("模型文件保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void replaceModelFile(UserAccount user, Project project, UUID modelId, String storedFileName, byte[] bytes) {
        try {
            Path directory = modelDirectory(user, project, modelId);
            Files.createDirectories(directory);
            Files.write(directory.resolve(storedFileName), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException("模型文件更新失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void storeExport(UserAccount user, Project project, UUID modelId, String fileName, byte[] bytes) {
        try {
            Path exportsDir = exportsDirectory(user, project, modelId);
            Files.createDirectories(exportsDir);
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
            Path exportPath = exportsDir.resolve(timestamp + "_" + fileName);
            Files.write(exportPath, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new BusinessException("导出文件保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Path resolveStoredPath(String storagePath) {
        Path resolved = storageRoot.resolve(storagePath).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new BusinessException("非法存储路径", HttpStatus.BAD_REQUEST);
        }
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            throw new BusinessException("模型文件不存在", HttpStatus.NOT_FOUND);
        }
        return resolved;
    }

    public String toRelativeStoragePath(Path absolutePath) {
        return storageRoot.relativize(absolutePath.normalize()).toString().replace('\\', '/');
    }

    public String readViewerConfig(UserAccount user, Project project, UUID modelId) {
        Path configPath = viewerConfigPath(user, project, modelId);
        try {
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath.getParent());
                Files.writeString(configPath, DEFAULT_VIEWER_CONFIG, StandardOpenOption.CREATE_NEW);
            }
            return Files.readString(configPath);
        } catch (IOException ex) {
            throw new BusinessException("读取查看器配置失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void writeViewerConfig(UserAccount user, Project project, UUID modelId, String jsonPayload) {
        try {
            Path configPath = viewerConfigPath(user, project, modelId);
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, jsonPayload, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException("保存查看器配置失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            throw new BusinessException("读取文件大小失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void copyRange(Path source, OutputStream outputStream, long start, long endInclusive) throws IOException {
        try (InputStream inputStream = Files.newInputStream(source, StandardOpenOption.READ)) {
            inputStream.skipNBytes(start);
            long remaining = endInclusive - start + 1;
            byte[] buffer = new byte[8192];
            while (remaining > 0) {
                int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                outputStream.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }
}
