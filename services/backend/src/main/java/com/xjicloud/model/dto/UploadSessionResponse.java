package com.xjicloud.model.dto;

import java.util.UUID;

public record UploadSessionResponse(
        UUID sessionId,
        int chunkSizeBytes,
        long receivedBytes,
        long sizeBytes
) {}
