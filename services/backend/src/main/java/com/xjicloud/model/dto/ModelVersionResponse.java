package com.xjicloud.model.dto;

import java.time.Instant;

public record ModelVersionResponse(
        String id,
        String fileName,
        long sizeBytes,
        Instant createdAt,
        boolean current,
        int version
) {
}
