package com.xjicloud.worker.dto;

import com.xjicloud.worker.WorkerStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkerResponse(
        UUID id,
        String name,
        WorkerStatus status,
        String gpuInfo,
        Instant lastHeartbeat,
        UUID currentJobId,
        Instant registeredAt
) {
}
