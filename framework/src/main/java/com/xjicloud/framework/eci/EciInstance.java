package com.xjicloud.framework.eci;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eci_instances")
public class EciInstance {

    @Id
    private UUID id = UUID.randomUUID();

    private String containerGroupId;

    private String name;

    @Enumerated(EnumType.STRING)
    private EciStatus status = EciStatus.PENDING;

    private String linkedWorkerName;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getContainerGroupId() { return containerGroupId; }
    public void setContainerGroupId(String containerGroupId) { this.containerGroupId = containerGroupId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public EciStatus getStatus() { return status; }
    public void setStatus(EciStatus status) { this.status = status; }
    public String getLinkedWorkerName() { return linkedWorkerName; }
    public void setLinkedWorkerName(String linkedWorkerName) { this.linkedWorkerName = linkedWorkerName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
