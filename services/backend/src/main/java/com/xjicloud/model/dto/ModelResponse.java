package com.xjicloud.model.dto;

import com.xjicloud.model.ModelFormat;
import java.time.Instant;
import java.util.UUID;

public record ModelResponse(
        UUID id,
        UUID projectId,
        String fileName,
        ModelFormat format,
        long sizeBytes,
        int version,
        Instant createdAt,
        Instant updatedAt
) {}
