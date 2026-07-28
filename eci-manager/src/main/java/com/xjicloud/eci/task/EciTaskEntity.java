package com.xjicloud.eci.task;

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
 * 本地持久化的任务记录（H2 表 {@code eci_task}）。
 * 实际使用的镜像、命令与规格写入本表，避免后续默认配置变化影响历史追溯。
 */
@Entity
@Table(name = "eci_task")
public class EciTaskEntity {

    @Id
    private UUID id;

    @Column(length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    /** 关联本地实例 UUID；实例删除后任务历史仍保留。 */
    @Column
    private UUID instanceId;

    @Column(nullable = false, length = 512)
    private String image;

    @Lob
    @Column
    private String commandJson;

    @Lob
    @Column
    private String argsJson;

    @Column(nullable = false)
    private double cpu;

    @Column(nullable = false)
    private double memory;

    @Column(length = 128)
    private String instanceType;

    @Column
    private Integer gpu;

    /** 可选；镜像缓存 ID（透传 ECI ImageSnapshotId）。 */
    @Column(length = 128)
    private String imageSnapshotId;

    @Column(nullable = false, length = 32)
    private String restartPolicy;

    @Column(nullable = false)
    private int maxRuntimeSec;

    @Lob
    @Column
    private String envJson;

    @Column(length = 1024)
    private String failureReason;

    /** 任务进入 RUNNING 的时间；用于超时判断。 */
    @Column
    private Instant startedAt;

    /** 进入终态的时间。 */
    @Column
    private Instant endedAt;

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

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(UUID instanceId) {
        this.instanceId = instanceId;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getCommandJson() {
        return commandJson;
    }

    public void setCommandJson(String commandJson) {
        this.commandJson = commandJson;
    }

    public String getArgsJson() {
        return argsJson;
    }

    public void setArgsJson(String argsJson) {
        this.argsJson = argsJson;
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

    public String getInstanceType() {
        return instanceType;
    }

    public void setInstanceType(String instanceType) {
        this.instanceType = instanceType;
    }

    public Integer getGpu() {
        return gpu;
    }

    public void setGpu(Integer gpu) {
        this.gpu = gpu;
    }

    public String getImageSnapshotId() {
        return imageSnapshotId;
    }

    public void setImageSnapshotId(String imageSnapshotId) {
        this.imageSnapshotId = imageSnapshotId;
    }

    public String getRestartPolicy() {
        return restartPolicy;
    }

    public void setRestartPolicy(String restartPolicy) {
        this.restartPolicy = restartPolicy;
    }

    public int getMaxRuntimeSec() {
        return maxRuntimeSec;
    }

    public void setMaxRuntimeSec(int maxRuntimeSec) {
        this.maxRuntimeSec = maxRuntimeSec;
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

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
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
