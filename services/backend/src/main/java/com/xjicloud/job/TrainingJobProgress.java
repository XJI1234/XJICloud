package com.xjicloud.job;

import com.xjicloud.sse.JobProgressSseService;
import java.time.Instant;
import java.util.UUID;

record TrainingJobProgress(
        UUID jobId,
        JobStatus status,
        int progress,
        String stage,
        String message,
        String computeStatus,
        String cloudStatus,
        String computeStatusReason,
        boolean cancellationPending,
        boolean cleanupPending,
        Instant updatedAt
) {

    static TrainingJobProgress from(TrainingJob job) {
        return new TrainingJobProgress(
                job.getId(), job.getStatus(), job.getProgress(), job.getStage(), job.getMessage(),
                job.getComputeStatus(), job.getCloudStatus(), job.getComputeStatusReason(),
                job.isEciCancellationPending(), job.isEciCleanupPending(), job.getUpdatedAt()
        );
    }

    void publish(JobProgressSseService sse) {
        sse.publish(jobId, status, progress, stage, message, computeStatus, cloudStatus,
                computeStatusReason, cancellationPending, cleanupPending, updatedAt);
    }
}
