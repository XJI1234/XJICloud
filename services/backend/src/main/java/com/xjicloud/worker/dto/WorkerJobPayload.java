package com.xjicloud.worker.dto;

import java.util.List;
import java.util.UUID;

public record WorkerJobPayload(
        UUID jobId,
        String inputOssPrefix,
        List<WorkerImageItem> images,
        String outputUploadUrl,
        String outputOssKey
) {
}
