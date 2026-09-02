package com.xjicloud.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.StorageProperties;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelUploadSessionServiceTest {

    @TempDir
    Path tempDir;

    private ModelUploadSessionService service() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new ModelUploadSessionService(new StorageProperties(tempDir.toString()), mapper);
    }

    @Test
    void writesSequentialChunksAndReplaysCompletedRange() throws Exception {
        ModelUploadSessionService sessions = service();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UploadSessionMeta created = sessions.create(userId, projectId, "a.ply", "ply", 6);
        byte[] first = {1, 2, 3, 4};
        UploadSessionMeta afterFirst = sessions.writeChunk(
                userId,
                created.sessionId(),
                ContentRange.parse("bytes 0-3/6"),
                new ByteArrayInputStream(first)
        );
        assertEquals(4, afterFirst.receivedBytes());

        UploadSessionMeta replay = sessions.writeChunk(
                userId,
                created.sessionId(),
                ContentRange.parse("bytes 0-3/6"),
                new ByteArrayInputStream(first)
        );
        assertEquals(4, replay.receivedBytes());

        UploadSessionMeta done = sessions.writeChunk(
                userId,
                created.sessionId(),
                ContentRange.parse("bytes 4-5/6"),
                new ByteArrayInputStream(new byte[] {5, 6})
        );
        assertEquals(6, done.receivedBytes());
        assertEquals(6, Files.size(sessions.payloadPathFor(userId, created.sessionId())));
    }

    @Test
    void rejectsHoles() throws Exception {
        ModelUploadSessionService sessions = service();
        UUID userId = UUID.randomUUID();
        UploadSessionMeta created = sessions.create(userId, UUID.randomUUID(), "a.ply", "ply", 6);
        assertThrows(BusinessException.class, () -> sessions.writeChunk(
                userId,
                created.sessionId(),
                ContentRange.parse("bytes 2-5/6"),
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4})
        ));
    }

    @Test
    void deleteSessionsForProjectLeavesOtherProjects() throws Exception {
        ModelUploadSessionService sessions = service();
        UUID userId = UUID.randomUUID();
        UUID keep = UUID.randomUUID();
        UUID drop = UUID.randomUUID();
        UploadSessionMeta kept = sessions.create(userId, keep, "keep.ply", "ply", 1);
        UploadSessionMeta dropped = sessions.create(userId, drop, "drop.ply", "ply", 1);
        sessions.deleteSessionsForProject(userId, drop);
        assertTrue(Files.exists(sessions.payloadPathFor(userId, kept.sessionId()).getParent()));
        assertFalse(Files.exists(sessions.payloadPathFor(userId, dropped.sessionId()).getParent()));
    }
}
