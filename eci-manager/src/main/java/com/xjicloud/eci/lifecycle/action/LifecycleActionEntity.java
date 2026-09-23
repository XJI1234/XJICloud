package com.xjicloud.eci.lifecycle.action;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eci_lifecycle_action", uniqueConstraints = @UniqueConstraint(
        name = "uk_eci_lifecycle_action_instance_type",
        columnNames = {"instance_id", "action_type"}))
public class LifecycleActionEntity {

    @Id
    private UUID id;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 16)
    private LifecycleActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LifecycleActionPhase phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LifecycleActionStatus status;

    @Column(length = 64)
    private String clientToken;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private UUID leaseToken;

    private Instant leaseUntil;

    /** Durable evidence that a Create request may have reached the provider. */
    private Instant createCallStartedAt;

    @Column(length = 1000)
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(UUID instanceId) {
        this.instanceId = instanceId;
    }

    public LifecycleActionType getActionType() {
        return actionType;
    }

    public void setActionType(LifecycleActionType actionType) {
        this.actionType = actionType;
    }

    public LifecycleActionPhase getPhase() {
        return phase;
    }

    public void setPhase(LifecycleActionPhase phase) {
        this.phase = phase;
    }

    public LifecycleActionStatus getStatus() {
        return status;
    }

    public void setStatus(LifecycleActionStatus status) {
        this.status = status;
    }

    public String getClientToken() {
        return clientToken;
    }

    public void setClientToken(String clientToken) {
        this.clientToken = clientToken;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public UUID getLeaseToken() {
        return leaseToken;
    }

    public void setLeaseToken(UUID leaseToken) {
        this.leaseToken = leaseToken;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(Instant leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    public Instant getCreateCallStartedAt() { return createCallStartedAt; }

    public void setCreateCallStartedAt(Instant createCallStartedAt) { this.createCallStartedAt = createCallStartedAt; }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
