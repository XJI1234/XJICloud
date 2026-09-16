package com.xjicloud.eci.instance.dto;

import java.util.List;
import java.util.Map;

/**
 * 创建实例请求体；字段均可选，未传时使用 {@code eci.default-*} 配置。
 */
public class CreateInstanceRequest {

    /** 显示名称；未传则自动生成 demo-xxxxxxxx。 */
    private String name;
    /** 容器镜像；未传则用默认镜像。 */
    private String image;
    private Double cpu;
    private Double memory;
    /** 透传到容器的环境变量（如日后 Worker 对接用）。 */
    private Map<String, String> env;
    /** 可选；指定 ECS 规格创建 ECI（如 {@code ecs.gn7i-c32g1.8xlarge}）。 */
    private String instanceType;
    /** 可选；容器 GPU 个数；指定 GPU 规格时建议传 1。 */
    private Integer gpu;
    /** 可选；镜像缓存 ID（如 imc-xxx），透传阿里云 ImageSnapshotId。 */
    private String imageSnapshotId;
    /**
     * 可选；挂 Memory EmptyDir 到 {@code /dev/shm} 的大小，如 {@code 8Gi} / {@code 256Mi}。
     * 未传则用 {@code eci.default-shm-size}；二者皆空则保持 ECI 默认 64Mi。
     */
    private String shmSize;
    /** 可选；Never / OnFailure / Always；未传则用 {@code eci.default-restart-policy}。 */
    private String restartPolicy;
    /** 可选；覆盖镜像 ENTRYPOINT。 */
    private List<String> command;
    /** 可选；覆盖镜像 CMD。 */
    private List<String> args;

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

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
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
}
