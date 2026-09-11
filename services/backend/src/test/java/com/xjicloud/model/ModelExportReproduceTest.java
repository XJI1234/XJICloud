package com.xjicloud.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.StorageProperties;
import com.xjicloud.project.Project;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ModelExportReproduceTest {

    @TempDir
    Path tempDir;

    private LocalFileStoreService files;
    private ModelService modelService;
    private Map<UUID, ModelVersionEntity> versionStore;
    private UserAccount user;
    private Project project;
    private UUID modelId;
    private ModelAsset asset;

    @BeforeEach
    void setUp() throws Exception {
        files = new LocalFileStoreService(new StorageProperties(tempDir.toString()));
        files.init();
        versionStore = new LinkedHashMap<>();
        user = new UserAccount();
        user.setId(UUID.randomUUID());
        project = new Project();
        project.setId(UUID.randomUUID());
        project.setOwnerId(user.getId());
        modelId = UUID.randomUUID();

        var live = new MockMultipartFile(
                "file",
                "scan.ply",
                "application/octet-stream",
                "LIVE-V1".getBytes(StandardCharsets.UTF_8)
        );
        files.storeUploadedModel(user, project, modelId, live, "original.ply");
        Path stored = files.modelFilePath(user, project, modelId, "original.ply");

        asset = new ModelAsset();
        asset.setId(modelId);
        asset.setProjectId(project.getId());
        asset.setFileName("scan.ply");
        asset.setFormat(ModelFormat.PLY);
        asset.setSizeBytes(files.fileSize(stored));
        asset.setStoragePath(files.toRelativeStoragePath(stored));
        asset.setVersion(1);

        ModelAssetRepository assets = proxyAssets();
        ModelVersionRepository versions = proxyVersions();
        modelService = new ModelService(
                assets,
                null,
                versions,
                null,
                files,
                null,
                null,
                new ObjectMapper()
        );
    }

    @Test
    void firstExportKeepsOriginalLiveBytesInHistory() throws Exception {
        var exported = new MockMultipartFile(
                "file",
                "scan.ply",
                "application/octet-stream",
                "NEW-V2".getBytes(StandardCharsets.UTF_8)
        );

        modelService.writeExport(user, project, asset, exported);

        Path livePath = files.modelFilePath(user, project, modelId, "original.ply");
        byte[] liveAfter = Files.readAllBytes(livePath);
        List<LocalFileStoreService.ExportArchive> archives = files.listExports(user, project, modelId);
        byte[] archived = files.readExport(user, project, modelId, archives.get(0).archiveName());

        debugLog("A", "after writeExport", liveAfter, archived, archives.size());

        assertEquals("NEW-V2", new String(liveAfter, StandardCharsets.UTF_8));
        assertEquals("LIVE-V1", new String(archived, StandardCharsets.UTF_8));
        assertEquals(2, asset.getVersion());
        assertEquals(1, versionStore.size());
        assertEquals(1, versionStore.values().iterator().next().getVersion());
        var listed = modelService.listOwnedVersions(user, project, asset);
        assertEquals("current", listed.get(0).id());
        assertEquals(2, listed.get(0).version());
        assertEquals(1, listed.get(1).version());
        assertFalse(listed.get(1).current());
    }

    @Test
    void restoreCopiesSnapshotAndArchivesLive() throws Exception {
        modelService.writeExport(
                user,
                project,
                asset,
                new MockMultipartFile("file", "scan.ply", "application/octet-stream", "NEW-V2".getBytes(StandardCharsets.UTF_8))
        );
        UUID historyId = versionStore.values().iterator().next().getId();

        modelService.writeRestore(user, project, asset, historyId);

        byte[] liveAfter = Files.readAllBytes(files.modelFilePath(user, project, modelId, "original.ply"));
        debugLog("C", "after writeRestore", liveAfter, "LIVE-V1".getBytes(StandardCharsets.UTF_8), files.listExports(user, project, modelId).size());
        assertEquals("LIVE-V1", new String(liveAfter, StandardCharsets.UTF_8));
        assertEquals(3, asset.getVersion());
        assertEquals(2, versionStore.size());
    }

    @Test
    void replacingPlyWithSpzDeletesOldOriginal() throws Exception {
        Path dir = files.modelDirectory(user, project, modelId);
        assertTrue(Files.exists(dir.resolve("original.ply")));
        files.replaceModelFile(
                user,
                project,
                modelId,
                "original.spz",
                new java.io.ByteArrayInputStream("SPZ".getBytes(StandardCharsets.UTF_8))
        );
        assertTrue(Files.exists(dir.resolve("original.spz")));
        assertFalse(Files.exists(dir.resolve("original.ply")));
    }

    @Test
    void resolveExportPathRejectsTraversal() {
        assertThrows(BusinessException.class, () -> files.resolveExportPath(user, project, modelId, "../secret.ply"));
        assertThrows(BusinessException.class, () -> files.resolveExportPath(user, project, modelId, "..\\secret.ply"));
    }

    private ModelAssetRepository proxyAssets() {
        return (ModelAssetRepository) Proxy.newProxyInstance(
                ModelAssetRepository.class.getClassLoader(),
                new Class[] {ModelAssetRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> args[0];
                    case "findById" -> Optional.of(asset);
                    case "toString" -> "proxy-assets";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private ModelVersionRepository proxyVersions() {
        return (ModelVersionRepository) Proxy.newProxyInstance(
                ModelVersionRepository.class.getClassLoader(),
                new Class[] {ModelVersionRepository.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "save" -> {
                            ModelVersionEntity row = (ModelVersionEntity) args[0];
                            versionStore.put(row.getId(), row);
                            yield row;
                        }
                        case "findByModelIdOrderByVersionDesc" -> versionStore.values().stream()
                                .filter(row -> row.getModelId().equals(args[0]))
                                .sorted(Comparator.comparingInt(ModelVersionEntity::getVersion).reversed())
                                .toList();
                        case "existsByModelId" -> versionStore.values().stream().anyMatch(row -> row.getModelId().equals(args[0]));
                        case "existsByModelIdAndVersion" -> versionStore.values().stream()
                                .anyMatch(row -> row.getModelId().equals(args[0]) && row.getVersion() == ((Number) args[1]).intValue());
                        case "findByIdAndModelId" -> {
                            ModelVersionEntity row = versionStore.get(args[0]);
                            yield row != null && row.getModelId().equals(args[1]) ? Optional.of(row) : Optional.empty();
                        }
                        case "deleteByModelId" -> {
                            versionStore.values().removeIf(row -> row.getModelId().equals(args[0]));
                            yield null;
                        }
                        case "toString" -> "proxy-versions";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                }
        );
    }

    private void debugLog(String hypothesisId, String message, byte[] liveAfter, byte[] archived, int archiveCount) throws Exception {
        // #region agent log
        String liveText = new String(liveAfter, StandardCharsets.UTF_8);
        String archiveText = new String(archived, StandardCharsets.UTF_8);
        String data = "{\"liveText\":\"" + liveText + "\",\"archiveText\":\"" + archiveText
                + "\",\"archiveCount\":" + archiveCount + ",\"sameAsIncoming\":"
                + liveText.equals(archiveText) + "}";
        String line = "{\"sessionId\":\"14ec0c\",\"runId\":\"post-fix\",\"hypothesisId\":\"" + hypothesisId
                + "\",\"location\":\"ModelExportReproduceTest\",\"message\":\"" + message
                + "\",\"data\":" + data + ",\"timestamp\":" + System.currentTimeMillis() + "}\n";
        Files.writeString(
                Path.of("d:/WeChatProjects/XJICloud/debug-14ec0c.log"),
                line,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
        // #endregion
    }
}
