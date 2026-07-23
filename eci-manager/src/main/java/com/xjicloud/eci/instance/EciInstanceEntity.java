package com.xjicloud.eci.instance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 本地持久化的 ECI 实例记录（H2 表 {@code eci_instance}）。
 * 本地 {@link InstanceStatus} 为权威状态；{@code cloudInstanceId}/{@code cloudStatus} 为云侧镜像。
 */
@Entity
@Table(name = "eci_instance")
public class EciInstanceEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 512)
    private String image;

    @Column(nullable = false)
    private double cpu;

    @Column(nullable = false)
    private double memory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InstanceStatus status;

    /** 云侧容器组 ID；Mock 下为 mock-eci-... */
    @Column(length = 128)
    private String cloudInstanceId;

    /** 最近一次 Describe 得到的云侧状态字符串。 */
    @Column(length = 64)
    private String cloudStatus;

    @Lob
    @Column
    private String envJson;

    @Column(length = 1024)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public double getCpu() {
        return cpu;
    }

    public void setCpu(double cpu) {
        this.cpu = cpu;
    }

    public double getMemory() {
        return memory;
    }

    public void setMemory(double memory) {
        this.memory = memory;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public void setStatus(InstanceStatus status) {
        this.status = status;
    }

    public String getCloudInstanceId() {
        return cloudInstanceId;
    }

    public void setCloudInstanceId(String cloudInstanceId) {
        this.cloudInstanceId = cloudInstanceId;
    }

    public String getCloudStatus() {
        return cloudStatus;
    }

    public void setCloudStatus(String cloudStatus) {
        this.cloudStatus = cloudStatus;
    }

    public String getEnvJson() {
        return envJson;
    }

    public void setEnvJson(String envJson) {
        this.envJson = envJson;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
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
