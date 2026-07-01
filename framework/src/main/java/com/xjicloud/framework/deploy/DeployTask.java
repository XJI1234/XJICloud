package com.xjicloud.framework.deploy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deploy_tasks")
public class DeployTask {

    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    private DeployType type;

    private UUID targetNodeId;

    @Enumerated(EnumType.STRING)
    private DeployStatus status = DeployStatus.PENDING;

    @Column(length = 65536)
    private String log = "";

    private String createdBy;

    private Instant startedAt;

    private Instant finishedAt;

    @Column(length = 4096)
    private String paramsJson;

    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public DeployType getType() { return type; }
    public void setType(DeployType type) { this.type = type; }
    public UUID getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(UUID targetNodeId) { this.targetNodeId = targetNodeId; }
    public DeployStatus getStatus() { return status; }
    public void setStatus(DeployStatus status) { this.status = status; }
    public String getLog() { return log; }
    public void setLog(String log) { this.log = log; }
    public DeployType getDeployType() { return type; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
