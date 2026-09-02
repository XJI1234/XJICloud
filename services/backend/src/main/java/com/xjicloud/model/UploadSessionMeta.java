package com.xjicloud.model;

import java.time.Instant;
import java.util.UUID;

public record UploadSessionMeta(
        UUID sessionId,
        UUID userId,
        UUID projectId,
        String fileName,
        String storedExtension,
        long sizeBytes,
        long receivedBytes,
        Instant createdAt,
        Instant updatedAt
) {
    public UploadSessionMeta withReceivedBytes(long nextReceivedBytes) {
        return new UploadSessionMeta(
                sessionId,
                userId,
                projectId,
                fileName,
                storedExtension,
                sizeBytes,
                nextReceivedBytes,
                createdAt,
                Instant.now()
        );
    }
}
