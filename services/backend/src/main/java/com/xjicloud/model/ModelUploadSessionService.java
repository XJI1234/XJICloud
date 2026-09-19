package com.xjicloud.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ModelUploadSessionService {

    public static final long MAX_SIZE_BYTES = 2L * 1024 * 1024 * 1024;
    public static final int CHUNK_SIZE_BYTES = 8 * 1024 * 1024;
    public static final Duration SESSION_TTL = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(ModelUploadSessionService.class);

    private final Path storageRoot;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<UUID, Object> sessionLocks = new ConcurrentHashMap<>();

    public ModelUploadSessionService(StorageProperties storageProperties, ObjectMapper objectMapper) {
        this.storageRoot = Path.of(storageProperties.root()).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    public UploadSessionMeta create(UUID userId, UUID projectId, String fileName, String storedExtension, long sizeBytes)
            throws IOException {
        if (sizeBytes < 1 || sizeBytes > MAX_SIZE_BYTES) {
            throw new BusinessException("文件不能超过 2GB", HttpStatus.BAD_REQUEST);
        }
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        UploadSessionMeta meta = new UploadSessionMeta(
                sessionId,
                userId,
                projectId,
                UUID.randomUUID(),
                fileName,
                storedExtension,
                sizeBytes,
                0,
                now,
                now,
                false
        );
        Path directory = sessionDirectory(userId, sessionId);
        Files.createDirectories(directory);
        Files.createFile(payloadPath(directory));
        writeMeta(directory, meta);
        return meta;
    }

    public UploadSessionMeta requireOwned(UUID userId, UUID sessionId) {
        Path directory = sessionDirectory(userId, sessionId);
        if (!Files.isDirectory(directory)) {
            throw new BusinessException("上传已失效，请重新上传", HttpStatus.NOT_FOUND);
        }
        try {
            UploadSessionMeta meta = readMeta(directory);
            if (!meta.userId().equals(userId)) {
                throw new BusinessException("无权访问该上传", HttpStatus.FORBIDDEN);
            }
            return meta;
        } catch (IOException ex) {
            throw new BusinessException("读取上传进度失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public UploadSessionMeta writeChunk(UUID userId, UUID sessionId, ContentRange range, InputStream body) {
        Object lock = sessionLocks.computeIfAbsent(sessionId, ignored -> new Object());
        synchronized (lock) {
            try {
                UploadSessionMeta meta = requireOwned(userId, sessionId);
                Path directory = sessionDirectory(userId, sessionId);
                if (meta.completed()) {
                    return meta;
                }
                if (range.total() != meta.sizeBytes()) {
                    throw new BusinessException("上传范围与文件大小不一致", HttpStatus.BAD_REQUEST);
                }
                if (range.endInclusive() + 1 <= meta.receivedBytes() && range.start() < meta.receivedBytes()) {
                    return meta;
                }
                if (range.start() != meta.receivedBytes()) {
                    throw new BusinessException("请从断点继续上传", HttpStatus.CONFLICT);
                }
                long expected = range.length();
                long written = appendChunk(payloadPath(directory), range.start(), body, expected);
                if (written != expected) {
                    throw new BusinessException("上传数据不完整，请重试", HttpStatus.BAD_REQUEST);
                }
                UploadSessionMeta updated = meta.withReceivedBytes(meta.receivedBytes() + written);
                writeMeta(directory, updated);
                return updated;
            } catch (BusinessException ex) {
                throw ex;
            } catch (IOException ex) {
                throw new BusinessException("模型文件保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
            } finally {
                discardBody(body);
            }
        }
    }

    public void markCompleted(UUID userId, UUID sessionId) {
        Object lock = sessionLocks.computeIfAbsent(sessionId, ignored -> new Object());
        synchronized (lock) {
            Path directory = sessionDirectory(userId, sessionId);
            try {
                UploadSessionMeta meta = requireOwned(userId, sessionId);
                writeMeta(directory, meta.withCompleted());
            } catch (IOException ex) {
                throw new BusinessException("模型文件保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    public Path payloadPathFor(UUID userId, UUID sessionId) {
        return payloadPath(sessionDirectory(userId, sessionId));
    }

    public void deleteSession(UUID userId, UUID sessionId) {
        deleteRecursively(sessionDirectory(userId, sessionId));
        sessionLocks.remove(sessionId);
    }

    public void deleteSessionsForProject(UUID userId, UUID projectId) {
        Path userUploads = uploadsRoot().resolve(userId.toString());
        if (!Files.isDirectory(userUploads)) {
            return;
        }
        try (Stream<Path> sessions = Files.list(userUploads)) {
            sessions.filter(Files::isDirectory).forEach(directory -> {
                try {
                    UploadSessionMeta meta = readMeta(directory);
                    if (meta.projectId().equals(projectId)) {
                        deleteRecursively(directory);
                        sessionLocks.remove(meta.sessionId());
                    }
                } catch (IOException ex) {
                    log.warn("Failed to inspect upload session {}: {}", directory, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.warn("Failed to list upload sessions for user {}: {}", userId, ex.getMessage());
        }
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void purgeExpired() {
        Path root = uploadsRoot();
        if (!Files.isDirectory(root)) {
            return;
        }
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        try (Stream<Path> users = Files.walk(root, 2)) {
            users.filter(path -> path.getFileName().toString().equals("meta.json")).forEach(metaPath -> {
                try {
                    UploadSessionMeta meta = objectMapper.readValue(metaPath.toFile(), UploadSessionMeta.class);
                    if (meta.updatedAt().isBefore(cutoff)) {
                        deleteRecursively(metaPath.getParent());
                        sessionLocks.remove(meta.sessionId());
                    }
                } catch (IOException ex) {
                    log.warn("Failed to purge upload session at {}: {}", metaPath, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.warn("Failed to walk upload sessions: {}", ex.getMessage());
        }
    }

    private long appendChunk(Path payload, long start, InputStream body, long expected) throws IOException {
        long written = 0;
        try (FileChannel channel = FileChannel.open(payload, StandardOpenOption.WRITE)) {
            channel.position(start);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while (written < expected && (read = body.read(buffer, 0, (int) Math.min(buffer.length, expected - written))) >= 0) {
                ByteBuffer wrap = ByteBuffer.wrap(buffer, 0, read);
                while (wrap.hasRemaining()) {
                    channel.write(wrap);
                }
                written += read;
            }
        }
        return written;
    }

    private void discardBody(InputStream body) {
        try {
            body.transferTo(OutputStream.nullOutputStream());
        } catch (IOException ignored) {
            // Client may already have abandoned the connection.
        }
    }

    private Path uploadsRoot() {
        return storageRoot.resolve("uploads");
    }

    private Path sessionDirectory(UUID userId, UUID sessionId) {
        return uploadsRoot().resolve(userId.toString()).resolve(sessionId.toString());
    }

    private Path payloadPath(Path directory) {
        return directory.resolve("payload.bin");
    }

    private void writeMeta(Path directory, UploadSessionMeta meta) throws IOException {
        objectMapper.writeValue(directory.resolve("meta.json").toFile(), meta);
    }

    private UploadSessionMeta readMeta(Path directory) throws IOException {
        return objectMapper.readValue(directory.resolve("meta.json").toFile(), UploadSessionMeta.class);
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException e) {
                    log.warn("Failed to delete {}: {}", item, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Failed to walk {}: {}", path, e.getMessage());
        }
    }

    public static String storedExtension(ModelFormat format) {
        return format.name().toLowerCase(Locale.ROOT);
    }
}
