package com.xjicloud.job.dto;

import com.xjicloud.job.JobStatus;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        UUID projectId,
        String name,
        JobStatus status,
        int progress,
        String stage,
        String message,
        String downloadUrl,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
