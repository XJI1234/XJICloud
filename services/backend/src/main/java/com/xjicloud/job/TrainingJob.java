package com.xjicloud.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "training_jobs")
public class TrainingJob {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 256)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status = JobStatus.PENDING;

    @Column(nullable = false)
    private int progress = 0;

    @Column(length = 128)
    private String stage;

    @Column(length = 512)
    private String message;

    @Column(length = 512)
    private String inputOssPrefix;

    @Column(length = 512)
    private String outputOssKey;

    @Column(length = 1024)
    private String outputDownloadUrl;

    private UUID workerId;

    @Column(nullable = false)
    private int dispatchAttempt;

    @Column(length = 128, unique = true)
    private String clientRequestId;

    private UUID eciTaskId;

    private Instant dispatchClaimedAt;

    private Instant dispatchRequestSentAt;

    @Column(nullable = false)
    private boolean dispatchClaimActive;

    private UUID dispatchClaimToken;

    private Instant dispatchEnqueuedAt;

    private Instant dispatchNextAttemptAt;

    @Column(nullable = false)
    private boolean eciCancellationPending;

    @Column(length = 128)
    private String computeStatus;

    @Column(length = 128)
    private String cloudStatus;

    @Column(length = 512)
    private String computeStatusReason;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean eciCleanupConfirmed;

    @Column(length = 1024)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getInputOssPrefix() {
        return inputOssPrefix;
    }

    public void setInputOssPrefix(String inputOssPrefix) {
        this.inputOssPrefix = inputOssPrefix;
    }

    public String getOutputOssKey() {
        return outputOssKey;
    }

    public void setOutputOssKey(String outputOssKey) {
        this.outputOssKey = outputOssKey;
    }

    public String getOutputDownloadUrl() {
        return outputDownloadUrl;
    }

    public void setOutputDownloadUrl(String outputDownloadUrl) {
        this.outputDownloadUrl = outputDownloadUrl;
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public void setWorkerId(UUID workerId) {
        this.workerId = workerId;
    }

    public int getDispatchAttempt() {
        return dispatchAttempt;
    }

    public void setDispatchAttempt(int dispatchAttempt) {
        this.dispatchAttempt = dispatchAttempt;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public UUID getEciTaskId() {
        return eciTaskId;
    }

    public void setEciTaskId(UUID eciTaskId) {
        this.eciTaskId = eciTaskId;
    }

    public Instant getDispatchClaimedAt() {
        return dispatchClaimedAt;
    }

    public void setDispatchClaimedAt(Instant dispatchClaimedAt) {
        this.dispatchClaimedAt = dispatchClaimedAt;
    }

    public Instant getDispatchRequestSentAt() {
        return dispatchRequestSentAt;
    }

    public void setDispatchRequestSentAt(Instant dispatchRequestSentAt) {
        this.dispatchRequestSentAt = dispatchRequestSentAt;
    }

    public boolean isDispatchClaimActive() {
        return dispatchClaimActive;
    }

    public void setDispatchClaimActive(boolean dispatchClaimActive) {
        this.dispatchClaimActive = dispatchClaimActive;
    }

    public UUID getDispatchClaimToken() {
        return dispatchClaimToken;
    }

    public void setDispatchClaimToken(UUID dispatchClaimToken) {
        this.dispatchClaimToken = dispatchClaimToken;
    }

    public Instant getDispatchEnqueuedAt() {
        return dispatchEnqueuedAt;
    }

    public void setDispatchEnqueuedAt(Instant dispatchEnqueuedAt) {
        this.dispatchEnqueuedAt = dispatchEnqueuedAt;
    }

    public Instant getDispatchNextAttemptAt() {
        return dispatchNextAttemptAt;
    }

    public void setDispatchNextAttemptAt(Instant dispatchNextAttemptAt) {
        this.dispatchNextAttemptAt = dispatchNextAttemptAt;
    }

    public boolean isEciCancellationPending() {
        return eciCancellationPending;
    }

    public void setEciCancellationPending(boolean eciCancellationPending) {
        this.eciCancellationPending = eciCancellationPending;
    }

    public String getComputeStatus() {
        return computeStatus;
    }

    public void setComputeStatus(String computeStatus) {
        this.computeStatus = computeStatus;
    }

    public String getCloudStatus() {
        return cloudStatus;
    }

    public void setCloudStatus(String cloudStatus) {
        this.cloudStatus = cloudStatus;
    }

    public String getComputeStatusReason() {
        return computeStatusReason;
    }

    public void setComputeStatusReason(String computeStatusReason) {
        this.computeStatusReason = computeStatusReason;
    }

    public boolean isEciCleanupConfirmed() {
        return eciCleanupConfirmed;
    }

    public void setEciCleanupConfirmed(boolean eciCleanupConfirmed) {
        this.eciCleanupConfirmed = eciCleanupConfirmed;
    }

    public boolean isEciCleanupPending() {
        return !eciCleanupConfirmed && (eciTaskId != null || dispatchRequestSentAt != null);
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
