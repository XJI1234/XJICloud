package com.xjicloud.eci.task.dto;

import com.xjicloud.eci.task.storage.TaskStorageRequest;
import java.util.List;
import java.util.Map;

/**
 * 创建任务请求体。{@code image} 必填；其余可选，未传时使用 {@code eci.default-*} 配置。
 */
public class CreateTaskRequest {

    private String name;
    /** 必填：完整镜像地址。 */
    private String image;
    private List<String> command;
    private List<String> args;
    private Double cpu;
    private Double memory;
    private String instanceType;
    private Integer gpu;
    /** 可选；镜像缓存 ID（如 imc-xxx），透传阿里云 ImageSnapshotId。 */
    private String imageSnapshotId;
    /**
     * 可选；挂 Memory EmptyDir 到 {@code /dev/shm} 的大小，如 {@code 8Gi} / {@code 256Mi}。
     * 未传则用 {@code eci.default-shm-size}；二者皆空则保持 ECI 默认 64Mi。
     */
    private String shmSize;
    /** Never / OnFailure / Always。 */
    private String restartPolicy;
    /** 正整数；未传则用 {@code eci.default-max-runtime-sec}。 */
    private Integer maxRuntimeSec;
    private Map<String, String> env;
    /** 可选；OSS storage 块。有值时须 OSS 已启用；解析失败则整单 400，不落库。 */
    private TaskStorageRequest storage;

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

    public List<String> getCommand() {
        return command;
    }

    public void setCommand(List<String> command) {
        this.command = command;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public Double getCpu() {
        return cpu;
    }

    public void setCpu(Double cpu) {
        this.cpu = cpu;
    }

    public Double getMemory() {
        return memory;
    }

    public void setMemory(Double memory) {
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

    public String getShmSize() {
        return shmSize;
    }

    public void setShmSize(String shmSize) {
        this.shmSize = shmSize;
    }

    public String getRestartPolicy() {
        return restartPolicy;
    }

    public void setRestartPolicy(String restartPolicy) {
        this.restartPolicy = restartPolicy;
    }

    public Integer getMaxRuntimeSec() {
        return maxRuntimeSec;
    }

    public void setMaxRuntimeSec(Integer maxRuntimeSec) {
        this.maxRuntimeSec = maxRuntimeSec;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public TaskStorageRequest getStorage() {
        return storage;
    }

    public void setStorage(TaskStorageRequest storage) {
        this.storage = storage;
    }
}
