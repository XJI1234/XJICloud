package com.xjicloud.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UploadSessionMeta(
        UUID sessionId,
        UUID userId,
        UUID projectId,
        UUID modelId,
        String fileName,
        String storedExtension,
        long sizeBytes,
        long receivedBytes,
        Instant createdAt,
        Instant updatedAt,
        boolean completed
) {
    public UploadSessionMeta withReceivedBytes(long nextReceivedBytes) {
        return new UploadSessionMeta(
                sessionId,
                userId,
                projectId,
                modelId,
                fileName,
                storedExtension,
                sizeBytes,
                nextReceivedBytes,
                createdAt,
                Instant.now(),
                completed
        );
    }

    public UploadSessionMeta withCompleted() {
        return new UploadSessionMeta(
                sessionId,
                userId,
                projectId,
                modelId,
                fileName,
                storedExtension,
                sizeBytes,
                receivedBytes,
                createdAt,
                Instant.now(),
                true
        );
    }
}
