package com.xjicloud.eci.instance;

import com.xjicloud.eci.provider.CloudStatusReason;
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

    /** 最近一次云侧状态说明，敏感内容已替换为安全文案。 */
    @Column(length = 1024)
    private String statusReason;

    /** 云侧曾确认 Succeeded；删除期间的其他状态不能抹掉任务成功证据。 */
    @Column(name = "cloud_succeeded", nullable = false)
    private boolean cloudSucceeded;

    /** 云侧 Succeeded 时的安全状态说明，供清理完成后的任务快照使用。 */
    @Column(name = "cloud_succeeded_reason", length = 1024)
    private String cloudSucceededReason;

    @Lob
    @Column
    private String envJson;

    @Lob
    @Column
    private String commandJson;

    @Lob
    @Column
    private String argsJson;

    /** 关联任务 UUID；手动创建的实例为空。 */
    @Column
    private UUID taskId;

    /** 重启策略：Never / OnFailure / Always。 */
    @Column(length = 32)
    private String restartPolicy;

    /** 可选 ECS 规格。 */
    @Column(length = 128)
    private String instanceType;

    /** 可选 GPU 个数。 */
    @Column
    private Integer gpu;

    /** 可选；镜像缓存 ID（透传 ECI ImageSnapshotId）。 */
    @Column(length = 128)
    private String imageSnapshotId;

    /** 可选；规范化后的 shm SizeLimit（如 8Gi）；空则未挂 EmptyDir。 */
    @Column(length = 32)
    private String shmSize;

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
        recordCloudObservation(cloudStatus, statusReason);
    }

    public void recordCloudObservation(String cloudStatus, String statusReason) {
        String safeReason = CloudStatusReason.safe(statusReason);
        if (hasCloudSucceeded()) {
            cloudSucceeded = true;
            if (cloudSucceededReason == null) {
                cloudSucceededReason = CloudStatusReason.safe(this.statusReason);
            }
        }
        this.cloudStatus = cloudStatus;
        this.statusReason = safeReason;
        if (isSucceeded(cloudStatus) && !ignoresCloudSucceeded()) {
            this.cloudSucceeded = true;
            this.cloudSucceededReason = safeReason;
        }
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        recordCloudObservation(cloudStatus, statusReason);
    }

    public boolean hasCloudSucceeded() {
        return !ignoresCloudSucceeded() && (cloudSucceeded || isSucceeded(cloudStatus));
    }

    public String getCloudSucceededReason() {
        if (!hasCloudSucceeded()) {
            return null;
        }
        if (cloudSucceededReason != null) {
            return cloudSucceededReason;
        }
        return isSucceeded(cloudStatus) ? statusReason : null;
    }

    public boolean ignoresCloudSucceeded() {
        return "Always".equalsIgnoreCase(restartPolicy);
    }

    private static boolean isSucceeded(String status) {
        return status != null && "Succeeded".equalsIgnoreCase(status);
    }

    public String getEnvJson() {
        return envJson;
    }

    public void setEnvJson(String envJson) {
        this.envJson = envJson;
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

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public String getRestartPolicy() {
        return restartPolicy;
    }

    public void setRestartPolicy(String restartPolicy) {
        this.restartPolicy = restartPolicy;
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

    public String getShmSize() {
        return shmSize;
    }

    public void setShmSize(String shmSize) {
        this.shmSize = shmSize;
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
